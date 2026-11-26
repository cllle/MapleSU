package com.maplesu.core.ui.webui
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import android.net.Uri
import android.webkit.WebResourceRequest

class WebUiDownloadTest {
    private fun waitFiles(cache:java.io.File,count:Int) {
        val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3)
        while(cache.listFiles()!!.size!=count) {if(System.nanoTime()>deadline)fail("download resources not reclaimed");Thread.sleep(2)}
    }

    @Test fun chunkedJsPassesTheReturnedFullUrlThroughAllNativeCalls() {
        val cache = Files.createTempDirectory("webui-chunk-").toFile()
        val handler = BlobDownloadHandler()
        try {
            val url = handler.registerBlobDownload("test.bin", "application/octet-stream", cache)
            assertTrue("JS sends the returned URL to writeDownloadChunk", handler.writeChunk(url, "AQID"))
            assertTrue(handler.completeDownload(url))
            val request = object : WebResourceRequest {
                override fun getUrl() = Uri.parse(url)
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = false
                override fun getMethod() = "GET"
                override fun getRequestHeaders() = emptyMap<String, String>()
            }
            val response = handler.shouldInterceptRequest(request)!!
            assertEquals(200, response.statusCode)
            assertArrayEquals(byteArrayOf(1,2,3), response.data!!.use { it.readBytes() })
            assertEquals(0, cache.listFiles()!!.size)
        } finally { handler.cleanup(); cache.deleteRecursively() }
    }

    @Test fun pagesHaveIndependentDownloadOwners() {
        val cache = Files.createTempDirectory("webui-pages-").toFile()
        val first = BlobDownloadHandler()
        val second = BlobDownloadHandler()
        try {
            val a = first.registerBlobDownload("first", null, cache)
            val b = second.registerBlobDownload("second", null, cache)
            assertFalse(second.writeChunk(a, "AQ=="))
            first.destroy()
            waitFiles(cache,1)
            assertTrue(second.writeChunk(b, "Ag=="))
            assertTrue(second.completeDownload(b))
            val file = second.takeDownload(b)!!
            assertArrayEquals(byteArrayOf(2), file.input.use { it.readBytes() })
            assertEquals(0, cache.listFiles()!!.size)
        } finally { first.destroy(); second.destroy(); waitFiles(cache,0); cache.deleteRecursively() }
    }

    @Test fun closedPagesRejectLateSessionsAndCloseStreamsAlreadyBeingRead() {
        val cache = Files.createTempDirectory("webui-close-").toFile()
        val handler = BlobDownloadHandler()
        try {
            val url = handler.registerBlobDownload("file", null, cache)
            assertTrue(handler.writeChunk(url, "AQID"))
            assertTrue(handler.completeDownload(url))
            val stream = handler.takeDownload(url)!!.input
            handler.destroy()
            waitFiles(cache,0)
            assertThrows(java.io.IOException::class.java) { stream.read() }
            assertThrows(IllegalStateException::class.java) { handler.registerBlobDownload("late", null, cache) }
            assertFalse(handler.writeChunk(url, "AA=="))
        } finally { handler.destroy(); waitFiles(cache,0); cache.deleteRecursively() }
    }

    @Test fun incompleteMalformedAndCancelledDownloadsKeepTheirRealResults() {
        val cache = Files.createTempDirectory("webui-errors-").toFile()
        val handler = BlobDownloadHandler()
        try {
            val url = handler.registerBlobDownload("file", null, cache)
            assertNull(handler.takeDownload(url))
            assertFalse(handler.writeChunk(url.replace("https:", "http:"), "AA=="))
            assertFalse(handler.writeChunk(url, "#invalid#"))
            assertFalse(handler.completeDownload(url))
            assertEquals(0, cache.listFiles()!!.size)
            val cancelled = handler.registerBlobDownload("cancelled", null, cache)
            handler.cancelDownload(cancelled)
            handler.cancelDownload(cancelled)
            assertFalse(handler.completeDownload(cancelled))
            assertEquals(0, cache.listFiles()!!.size)
        } finally { handler.destroy(); waitFiles(cache,0); cache.deleteRecursively() }
    }

    @Test fun oldBareIdsStillWorkAndACompletedFileIsConsumedOnce() {
        val cache = Files.createTempDirectory("webui-ids-").toFile()
        val handler = BlobDownloadHandler()
        try {
            val url = handler.registerBlobDownload("file", null, cache)
            val id = url.substringAfterLast('/')
            assertTrue(handler.writeChunk(id, "AQID"))
            assertTrue(handler.completeDownload(id))
            assertFalse(handler.completeDownload(id))
            assertFalse(handler.writeChunk(id, "AA=="))
            val file = handler.takeDownload(url)!!
            assertEquals(3L, file.size)
            assertNull(handler.takeDownload(id))
            file.input.close()
            assertNull(handler.takeDownload(id))
        } finally { handler.destroy(); waitFiles(cache,0); cache.deleteRecursively() }
    }

    @Test fun completeAndCancelCanRaceWithoutLeakingTheSpool() {
        val cache = Files.createTempDirectory("webui-race-").toFile()
        val handler = BlobDownloadHandler()
        try {
            repeat(60) {
                val url = handler.registerBlobDownload("file", null, cache)
                handler.writeChunk(url, "AQID")
                val begin = java.util.concurrent.CountDownLatch(1)
                val complete = Thread { begin.await(); handler.completeDownload(url) }
                val cancel = Thread { begin.await(); handler.cancelDownload(url) }
                complete.start(); cancel.start(); begin.countDown()
                complete.join(); cancel.join()
                assertNull(handler.takeDownload(url))
                assertEquals(0, cache.listFiles()!!.size)
            }
        } finally { handler.destroy(); waitFiles(cache,0); cache.deleteRecursively() }
    }

    @Test fun fileNamesAreContainedAndWritesPreserveTheCompleteByteStream() {
        assertEquals("test.bin", sanitizeWebUIDownloadFileName("../../test.bin"))
        assertEquals("test.bin", sanitizeWebUIDownloadFileName("C:\\folder\\test.bin"))
        assertEquals("download.bin", sanitizeWebUIDownloadFileName(".."))
        assertEquals("name_file", sanitizeWebUIDownloadFileName("name\nfile"))
        val input = ByteArray(180000) { (it % 251).toByte() }
        val output = java.io.ByteArrayOutputStream()
        var progress = 0L
        assertEquals(input.size.toLong(), writeWebUIDownload(input.inputStream(), output) { value -> assertTrue(value > progress); progress = value })
        assertArrayEquals(input, output.toByteArray())
    }
}

