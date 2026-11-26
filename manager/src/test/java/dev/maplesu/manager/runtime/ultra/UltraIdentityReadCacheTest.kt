package dev.maplesu.manager.runtime.ultra

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UltraIdentityReadCacheTest {
    private fun identity(source: Source) = InstalledIdentity(
        "a".repeat(64), "/data/example/init", 100,
        "maplesu-ultra-build", "boot", source,
    )

    @Test fun fallbackIdentityIsReturnedButNextReadRetriesTheDevice() {
        for (source in listOf(Source.Persisted, Source.Unknown)) {
            val cache = UltraIdentityReadCache({ 0L }, 60_000L)
            val fallback = identity(source)
            val live = identity(Source.Readback)
            var calls = 0
            assertEquals(fallback, cache.read("key", "boot") { calls++; fallback })
            assertEquals(live, cache.read("key", "boot") { calls++; live })
            assertEquals(live, cache.read("key", "boot") { calls++; error("readback should be cached") })
            assertEquals(2, calls)
        }
    }

    @Test fun forcedReadFailureDiscardsThePreviousLiveObservation() {
        for (source in listOf(Source.Persisted, Source.Unknown)) {
            val cache = UltraIdentityReadCache({ 0L }, 60_000L)
            val live = identity(Source.Readback)
            val fallback = identity(source)
            var calls = 0
            cache.read("key", "boot") { calls++; live }
            assertEquals(fallback, cache.read("key", "boot", force = true) { calls++; fallback })
            assertEquals(live, cache.read("key", "boot") { calls++; live })
            assertEquals(3, calls)
        }
    }

    @Test fun completedEventPeekIsPureAndRejectsExpiredOrChangedScope() {
        var now = 0L
        val cache = UltraIdentityReadCache({ now }, 60_000L)
        val live = identity(Source.Readback)
        assertNull(cache.peek("key", "boot"))
        cache.read("key", "boot") { live }
        assertEquals(live, cache.peek("key", "boot"))
        assertNull(cache.peek("another-key", "boot"))
        assertNull(cache.peek("key", "another-boot"))
        assertNull(cache.peek(null, "boot"))
        now = 60_000L
        assertNull(cache.peek("key", "boot"))
        now = 0L
        cache.invalidate()
        assertNull(cache.peek("key", "boot"))
    }

    @Test fun eventPeekDoesNotWaitForAnInFlightRead() {
        val cache = UltraIdentityReadCache({ 0L }, 60_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val worker = Thread {
            cache.read("key", "boot") { entered.countDown(); release.await(); identity(Source.Readback) }
        }.apply { start() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            Thread { assertNull(cache.peek("key", "boot")); completed.countDown() }.start()
            assertTrue("event handler joined the native worker", completed.await(1, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            worker.join(5_000)
        }
        assertFalse(worker.isAlive)
    }
}
