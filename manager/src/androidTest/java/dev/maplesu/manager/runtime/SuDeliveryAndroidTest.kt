package dev.maplesu.manager.runtime

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.Process
import dev.maplesu.delivery.probe.DeliveryProbeContract
import androidx.test.platform.app.InstrumentationRegistry
import dev.maplesu.manager.runtime.authorization.ManagerAuthorizations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Delivery, real per-application execution and foreign-UID denial for one installed target.
 *
 * The consumer-target fixture executes its own delivered image as its actual Linux UID.
 * The fixture APK's self-target instrumentation supplies a separate ordinary fullUID.
 * This class itself stays inside the Manager process, so it never proves Manager-dead;
 * the separate external observer must perform that acceptance with Manager force-stopped.
 */
class SuDeliveryAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val consumer = "dev.maplesu.consumer"

    private val fixture = "dev.maplesu.delivery.probe"
    private val arguments = InstrumentationRegistry.getArguments()

    private fun shell(command: String): String {
        val descriptor = AtomicReference<ParcelFileDescriptor?>()
        val stopped = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "delivery-fixture-shell").apply { isDaemon = true }
        }
        val pending = worker.submit<String> {
            val opened = instrumentation.uiAutomation.executeShellCommand(command)
            descriptor.set(opened)
            ParcelFileDescriptor.AutoCloseInputStream(opened).use {
                check(!stopped.get()) { "fixture shell already timed out" }
                String(DeliveryProbeContract.readBounded(it), Charsets.UTF_8)
            }
        }
        var primary: Throwable? = null
        try {
            return pending.get(30, TimeUnit.SECONDS)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            stopped.set(true)
            try { descriptor.get()?.close() }
            catch (cleanup: Throwable) {
                if (primary != null) primary.addSuppressed(cleanup) else throw cleanup
            } finally { pending.cancel(true); worker.shutdownNow() }
        }
    }

    private fun consumerUid(): Int = context.packageManager.getApplicationInfo(consumer, 0).uid

    @Test fun grantedApplicationRunsItsDeliveredClientWithoutManager() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyMapleSuBoot") == "true")
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifySuPolicyMutations") == "true")
        assertEquals("this test is Manager-resident", context.applicationInfo.uid, Process.myUid())
        val expectedConsumerSigner = requiredSigner("consumerSignerSha256")
        val expectedForeignSigner = requiredSigner("foreignSignerSha256")
        assertEquals("consumer signer changed", expectedConsumerSigner, signers(consumer))
        assertEquals("foreign signer changed", expectedForeignSigner, signers(fixture))
        val foreignUid = context.packageManager.getApplicationInfo(fixture, 0).uid
        val policy = ManagerAuthorizations.policy(context)
        val uid = consumerUid()
        DeliveryProbeContract.checkIdentity(uid, uid, context.applicationInfo.uid, foreignUid)
        DeliveryProbeContract.checkIdentity(foreignUid, foreignUid, context.applicationInfo.uid, uid)
        assertEquals("fixture must belong to the Manager's user", context.applicationInfo.uid / 100000, uid / 100000)
        assertEquals("foreign fixture must belong to the same user", uid / 100000, foreignUid / 100000)
        val granted = policy.hasCurrentGrant(uid)
        val enabled = policy.isEnabled
        val path = SuDelivery.deliveredPath(context, consumer)
        assertTrue("consumer has no library directory", path != null)
        var primary: Throwable? = null
        try {
            // (1) Refused while not granted: nothing may be written.
            policy.setEnabled(true)
            assertFalse("foreign fixture must have no active grant", policy.hasCurrentGrant(foreignUid))
            policy.revoke(uid)
            // Remove any image a previous run or a manual experiment left behind. The
            // application itself cannot unlink a file from its root-owned library
            // directory, so this uses the root withdrawal path.
            SuDelivery.withdraw(context, consumer)
            val refused = SuDelivery.deliver(context, consumer)
            assertFalse("an unauthorised application must be refused: ${refused.detail}", refused.ok)
            assertEquals("AUTH_NOT_GRANTED", refused.detail)
            assertFalse("a refused delivery must not create the image", File(path!!).exists())

            // (2) Grant, deliver and execute in the consumer UID; Manager remains alive here.
            policy.grant(uid)
            val delivered = SuDelivery.deliver(context, consumer)
            assertTrue("delivery failed: ${delivered.detail}", delivered.ok)
            assertEquals(path, delivered.path)
            val image = File(path)
            assertTrue("delivered image missing", image.isFile)
            // Metadata is supplementary. It does not stand in for execution below.
            val stat = runRoot("stat -c '%u %a' '$path' 2>&1")
            val label = runRoot("ls -Z '$path' 2>&1")
            android.util.Log.i("MapleSU", "MAPLESU_EVIDENCE delivery stat=[${stat.trim()}] label=[${label.trim()}]")
            val fields = stat.trim().split(Regex("\\s+"))
            assertEquals("delivered image owner must be the target uid", uid.toString(), fields.getOrNull(0))
            assertEquals("delivered image must be owner-only", "700", fields.getOrNull(1))
            assertTrue("unexpected label: $label", label.contains("apk_data_file"))
            val grantedReceipt = probe("granted", path, uid, foreignUid,
                expectedConsumerSigner, expectedForeignSigner)
            val foreignReceipt = probe("foreign", path, uid, foreignUid,
                expectedConsumerSigner, expectedForeignSigner)
            android.util.Log.i("MapleSU", "MAPLESU_EVIDENCE delivery granted=[$grantedReceipt] " +
                "foreign=[$foreignReceipt] managerResident=true managerDeadQualified=false")

            // (3) Withdrawal removes the image; that is the whole revocation mechanism
            //     under the accepted soft-revocation contract.
            val withdrawn = SuDelivery.withdraw(context, consumer)
            assertTrue("withdraw failed: ${withdrawn.detail}", withdrawn.ok)
            assertFalse("image survived withdrawal", File(path).exists())
            val afterStat = runRoot("test -e '$path' && echo PRESENT || echo ABSENT")
            assertTrue("withdrawn image is still present: $afterStat", afterStat.contains("ABSENT"))
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun restore(action: () -> Unit) {
                try { action() } catch (failure: Throwable) {
                    val existing = primary ?: cleanupFailure
                    if (existing != null) existing.addSuppressed(failure) else cleanupFailure = failure
                }
            }
            restore { check(SuDelivery.withdraw(context, consumer).ok) { "final withdrawal failed" } }
            restore { if (granted) policy.grant(uid) else policy.revoke(uid) }
            restore { policy.setEnabled(enabled) }
            if (primary == null) cleanupFailure?.let { throw it }
        }
    }

    private fun probe(mode: String, path: String, uid: Int, foreignUid: Int,
                      consumerSigner: String, foreignSigner: String): String {
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val extras = linkedMapOf(
            "mode" to mode, "nonce" to nonce, "consumerPackage" to consumer,
            "managerPackage" to context.packageName, "foreignPackage" to fixture,
            "consumerUid" to uid.toString(), "managerUid" to context.applicationInfo.uid.toString(),
            "foreignUid" to foreignUid.toString(), "consumerSignerSha256" to consumerSigner,
            "foreignSignerSha256" to foreignSigner, "deliveredPath" to path,
        )
        val entry = if (mode == "granted") "DeliveryFixtureInstrumentation" else "ForeignFixtureInstrumentation"
        val command = buildString {
            append("/system/bin/am instrument --user ").append(uid / 100000).append(" -w -r")
            extras.forEach { (key, value) ->
                append(" -e ").append(DeliveryProbeContract.quote(key))
                    .append(' ').append(DeliveryProbeContract.quote(value))
            }
            append(' ').append(DeliveryProbeContract.quote("$fixture/dev.maplesu.delivery.probe.$entry"))
        }
        return DeliveryProbeContract.verifyInstrumentation(shell(command), mode, nonce,
            if (mode == "granted") consumer else fixture, if (mode == "granted") uid else foreignUid)
    }

    private fun requiredSigner(name: String): String {
        val value = requireNotNull(arguments.getString(name)) { "missing $name" }
        require(value.matches(Regex("[0-9a-f]{64}(,[0-9a-f]{64})*"))) { "invalid signer set" }
        require(value.split(',') == value.split(',').distinct().sorted()) { "noncanonical signer set" }
        return value
    }

    private fun signers(name: String): String {
        val info = context.packageManager.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = requireNotNull(info.signingInfo).apkContentsSigners
        require(signers.isNotEmpty()) { "installed signer absent" }
        return signers.map { signer ->
            MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }.sorted().joinToString(",")
    }

    /** One short-lived direct-admission process: no libsu connection is held. */
    private fun runRoot(command: String): String {
        val result = DirectRoot.run(context, command)
        assertEquals("root metadata command failed", 0, result.code)
        return result.output
    }
}
