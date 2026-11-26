package dev.maplesu.manager.liteweb

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LiteWebModuleBridgeTest {
    @Test fun bridgeIsInjectedBeforeTheModulesFirstScript() {
        val html = "<!doctype html><html><head><script>ksu.io()</script></head><body></body></html>"
        val result = LiteWebModWeb.injectBridge(html, "<script>bridge()</script>")
        assertTrue(result.indexOf("bridge()") < result.indexOf("ksu.io()"))
        assertTrue(result.startsWith("<!doctype html>"))
    }

    @Test fun moduleSourceRejectsTraversalAndRetiresDisabledOrReplacedInstallations() {
        val directory = Files.createTempDirectory("module-web-source-").toFile()
        try {
            val module = File(directory, "fixture").apply { mkdirs() }
            val prop = File(module, "module.prop").apply { writeText("id=fixture\nversion=1\n") }
            File(module, "webroot").mkdirs()
            File(module, "webroot/index.html").writeText("<h1>fixture</h1>")
            val files = LiteWebModuleFiles(directory)
            val identity = files.identity("fixture")
            assertNotNull(identity)
            assertTrue(files.read("fixture", "index.html") is LiteWebModuleWeb.Found)
            assertTrue(files.read("fixture", "../module.prop") is LiteWebModuleWeb.NotFound)
            assertNull(files.module(".."))
            File(module, "disable").writeText("")
            assertNull(files.identity("fixture"))
            File(module, "disable").delete()
            prop.writeText("id=fixture\nversion=changed-installation\n")
            assertNotEquals(identity, files.identity("fixture"))
        } finally { directory.deleteRecursively() }
    }
}
