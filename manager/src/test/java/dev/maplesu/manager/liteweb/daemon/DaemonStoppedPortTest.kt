package dev.maplesu.manager.liteweb.daemon

import dev.maplesu.manager.liteweb.LiteWebPortPolicy
import dev.maplesu.manager.liteweb.liteWebPortIdentity
import dev.maplesu.manager.recovery.LocalRecoveryFs
import dev.maplesu.manager.recovery.RecoveryBundle
import dev.maplesu.manager.recovery.RecoveryPrepareResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket

class DaemonStoppedPortTest {
    @get:Rule val temp = TemporaryFolder()
    private val token = "a".repeat(64)

    private fun prepare(port: Int): Pair<File, RecoveryBundle> {
        val directory = File(temp.root, "recovery")
        val apk = temp.newFile("source.apk").apply { writeText("fixture-apk") }
        val bundle = RecoveryBundle(LocalRecoveryFs(), directory.absolutePath, locationPolicy = { true })
        assertTrue(bundle.prepare("dev.maplesu.manager", 1, apk.absolutePath,
            webuiConfig = "token=$token\nwebuiPort=$port\nworkMode=LITE\nretained=value\n".toByteArray()) is RecoveryPrepareResult.Ok)
        return directory to bundle
    }

    @Test fun occupiedOldPortCanMoveToFreePortWithoutChangingTokenOrOtherManifestItems() {
        ServerSocket(0).use { occupied ->
            val (directory, bundle) = prepare(occupied.localPort)
            val before = bundle.verify().manifest!!
            val requested = ServerSocket(0).use { it.localPort }
            assertNull(LiteWebDaemonConfig.updateStoppedPort(directory,
                liteWebPortIdentity(occupied.localPort, token), requested, "123"))
            val config = LiteWebDaemonConfig.load(File(directory, "webui.conf").absolutePath)!!
            assertEquals(LiteWebDaemonConfig(requested, token, "LITE"), config)
            assertTrue(File(directory, "webui.conf").readText().contains("retained=value"))
            val verified = bundle.verify()
            assertTrue(verified.problems.toString(), verified.ok)
            assertEquals(before.items.filter { it.role != "WEBUI_CONFIG" }, verified.manifest!!.items.filter { it.role != "WEBUI_CONFIG" })
            ServerSocket(requested).close()
        }
    }

    @Test fun activeOwnerAndTargetConflictKeepConfigAndManifestUntouched() {
        val originalPort = ServerSocket(0).use { it.localPort }
        val (directory, _) = prepare(originalPort)
        val config = File(directory, "webui.conf")
        val manifest = File(directory, "manifest.json")
        val original = config.readBytes()
        val originalManifest = manifest.readBytes()
        val identity = liteWebPortIdentity(originalPort, token)
        DaemonProcessLease.acquire(directory, "456")!!.use {
            assertEquals("LITEWEB_DAEMON_OWNER_ACTIVE", LiteWebDaemonConfig.updateStoppedPort(directory, identity, originalPort, "123"))
        }
        ServerSocket(0).use { conflict ->
            assertEquals(LiteWebPortPolicy.CODE_CONFLICT, LiteWebDaemonConfig.updateStoppedPort(directory, identity, conflict.localPort, "123"))
        }
        assertArrayEquals(original, config.readBytes())
        assertArrayEquals(originalManifest, manifest.readBytes())
    }

    @Test fun repeatedOldIdentityAndCorruptManifestNeverOverwriteTheCurrentConfig() {
        val originalPort = ServerSocket(0).use { it.localPort }
        val (directory, bundle) = prepare(originalPort)
        val identity = liteWebPortIdentity(originalPort, token)
        val requested = ServerSocket(0).use { it.localPort }
        assertNull(LiteWebDaemonConfig.updateStoppedPort(directory, identity, requested, "123"))
        val config = File(directory, "webui.conf")
        val manifest = File(directory, "manifest.json")
        val committed = config.readBytes()
        val committedManifest = manifest.readBytes()
        assertEquals("RECOVERY_CONFIG_CHANGED", LiteWebDaemonConfig.updateStoppedPort(directory, identity, requested, "123"))
        assertArrayEquals(committed, config.readBytes())
        assertArrayEquals(committedManifest, manifest.readBytes())
        assertTrue(bundle.verify().ok)
        manifest.writeText(committedManifest.toString(Charsets.UTF_8).replace("\"integritySha256\":\"", "\"integritySha256\":\"00"))
        val corrupt = manifest.readBytes()
        val nextPort = ServerSocket(0).use { it.localPort }
        assertEquals("RECOVERY_MANIFEST_CORRUPT", LiteWebDaemonConfig.updateStoppedPort(directory,
            liteWebPortIdentity(requested, token), nextPort, "123"))
        assertArrayEquals(committed, config.readBytes())
        assertArrayEquals(corrupt, manifest.readBytes())
    }
}
