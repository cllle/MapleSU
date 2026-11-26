package dev.maplesu.manager.jailbreak

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.*
import org.junit.Test

/** Host runner supplies only RootShellLease's Android socket boundary as Java pipes. */
class JailbreakLeaseLivenessTest {
    private class Peer(val reply: String = "OK alive=1", blocked: Boolean = false) : Closeable {
        private val requests = PipedInputStream()
        private val responses = PipedOutputStream()
        val lease = RootShellLease(PipedInputStream(responses), PipedOutputStream(requests))
        val requested = CountDownLatch(1)
        val release = CountDownLatch(if (blocked) 1 else 0)
        @Volatile var count = 0
        private val worker = Thread {
            try {
                val reader = BufferedReader(InputStreamReader(requests))
                while (true) {
                    val line = reader.readLine() ?: break
                    check(line.substringAfter(' ') == "ping")
                    count++
                    requested.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    responses.write((line.substringBefore(' ') + " " + reply + "\n").toByteArray())
                    responses.flush()
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }

        fun bind(attempt: String = "attempt") {
            JailbreakLeaseStore.record(lease, "fixture", attempt)
            JailbreakLeaseStore.confirmHandover()
        }

        override fun close() {
            release.countDown()
            runCatching { responses.close() }
            runCatching { requests.close() }
            lease.close()
            worker.join(1000)
        }
    }

    @Test fun pendingFirstPingDoesNotPublishAnUnobservedFalse() {
        Peer(blocked = true).use { peer ->
            val workers = Executors.newFixedThreadPool(2)
            try {
                peer.bind()
                val first = workers.submit<Boolean> { JailbreakLeaseStore.live }
                assertTrue(peer.requested.await(2, TimeUnit.SECONDS))
                val second = workers.submit<Boolean> { JailbreakLeaseStore.live }
                val premature = try { second.get(100, TimeUnit.MILLISECONDS) } catch (_: TimeoutException) { null }
                peer.release.countDown()
                assertTrue(first.get(2, TimeUnit.SECONDS))
                assertTrue(second.get(2, TimeUnit.SECONDS))
                assertNull("An unfinished ping has no cached Boolean verdict", premature)
                assertEquals(1, peer.count)
            } finally { JailbreakLeaseStore.clear(); workers.shutdownNow() }
        }
    }

    @Test fun completedFailedPingStaysFalse() {
        Peer(reply = "ERR executor-gone").use { peer ->
            try {
                peer.bind()
                assertFalse(JailbreakLeaseStore.live)
                assertFalse(JailbreakLeaseStore.live)
                assertEquals(1, peer.count)
            } finally { JailbreakLeaseStore.clear() }
        }
    }

    @Test fun transportDeathInvalidatesCompletedSuccess() {
        val peer = Peer()
        try {
            peer.bind()
            assertTrue(JailbreakLeaseStore.live)
            val transport = JailbreakLeaseStore.eventTransport()
            peer.close()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (transport.failure == null && System.nanoTime() < deadline) Thread.sleep(5)
            assertNotNull(transport.failure)
            assertFalse(JailbreakLeaseStore.live)
        } finally { JailbreakLeaseStore.clear(); peer.close() }
    }

    @Test fun cancellationInvalidatesCompletedSuccess() {
        Peer().use { peer ->
            try {
                peer.bind()
                assertTrue(JailbreakLeaseStore.live)
                JailbreakLeaseStore.cancelPending()
                assertFalse(JailbreakLeaseStore.live)
            } finally { JailbreakLeaseStore.clear() }
        }
    }

    @Test fun aTimedOutPingLeavesTheLeaseOffline() {
        Peer(blocked = true).use { peer ->
            try {
                peer.bind()
                val started = System.nanoTime()
                assertFalse(JailbreakLeaseStore.live)
                assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(4))
                assertEquals("JAILBREAK_LEASE_REPLY_TIMEOUT", JailbreakLeaseStore.eventTransport().failure)
                assertFalse(JailbreakLeaseStore.live)
                assertEquals(1, peer.count)
            } finally { JailbreakLeaseStore.clear() }
        }
    }

    @Test fun replacementCannotReuseThePreviousLeasesVerdict() {
        Peer(blocked = true).use { old ->
            Peer().use { replacement ->
                val worker = Executors.newSingleThreadExecutor()
                try {
                    old.bind("old")
                    val pending = worker.submit<Boolean> { JailbreakLeaseStore.live }
                    assertTrue(old.requested.await(2, TimeUnit.SECONDS))
                    replacement.bind("new")
                    old.release.countDown()
                    assertFalse(pending.get(2, TimeUnit.SECONDS))
                    assertTrue(JailbreakLeaseStore.live)
                    assertEquals("new", JailbreakLeaseStore.attemptId)
                    assertEquals(1, replacement.count)
                } finally { JailbreakLeaseStore.clear(); worker.shutdownNow() }
            }
        }
    }
}
