package com.maplesu.core.ui.webui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.maplesu.core.R
import com.maplesu.core.ui.util.DownloadCompletionAction
import com.maplesu.core.ui.util.DownloadManager
import com.maplesu.core.ui.util.DownloadService
import dev.maplesu.manager.workmode.ModeRuntime
import android.webkit.MimeTypeMap
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import kotlin.coroutines.coroutineContext

class WebUIDownloadInterface private constructor(
    private val state: WebUIState?,
    private val session: WebUiDownloadSession?,
) {
    constructor(state: WebUIState) : this(state, null)
    internal constructor(session: WebUiDownloadSession) : this(null, session)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val blobs = BlobDownloadHandler()
    private val destroyed = AtomicBoolean(false)
    private val webView get() = state?.webView
    private var pageView = state?.webView
    private val pageActivation = ModeRuntime.activation

    @Synchronized
    private fun pageOpen(): Boolean {
        if (destroyed.get() || pageActivation !== ModeRuntime.activation) return false
        if (session != null) return session.isCurrent()
        if (state?.isModuleSessionCurrent != true || state.uiEvent == WebUIEvent.Close) return false
        if (pageView == null) pageView = webView
        return pageView != null && pageView === webView
    }

    private fun ensurePageActive(context: kotlin.coroutines.CoroutineContext) {
        context.ensureActive()
        if (!pageOpen()) throw CancellationException("WebUI page retired")
    }

    companion object {
        // Use a different notification ID range to avoid collision with DownloadService
        private const val WEBUI_NOTIFICATION_ID_BASE = 200000
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        if (!pageOpen()) return
        val uri = url.toUri()
        if (uri.scheme != "http" && uri.scheme != "https") return
        if (session != null) { session.openExternal(url); return }
        val currentWebView = webView ?: return

        runCatching {
            currentWebView.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure { throwable ->
            Log.e("WebUIDownload", "Failed to open $url", throwable)
        }
    }

    @JavascriptInterface
    fun save(base64: String, fileName: String?) {
        saveFile(fileName, null) { ByteArrayInputStream(Base64.decode(base64, Base64.DEFAULT)) }
    }

    @Synchronized
    private fun saveFile(fileName: String?, requestedMimeType: String?, onFinished: () -> Unit = {}, source: () -> InputStream): Boolean {
        if (!pageOpen()) return false
        if (session != null) return session.save(fileName, requestedMimeType, onFinished, source)
        val currentWebView = webView ?: return false
        val context = currentWebView.context.applicationContext
        val displayName = sanitizeWebUIDownloadFileName(fileName)
        val mimeType = requestedMimeType?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val downloadId = DownloadManager.registerLocalSave(
            fileName = displayName,
            mimeType = mimeType,
            completionAction = DownloadCompletionAction.OPEN_FILE,
        )
        runCatching {
            ensureNotificationChannel(notificationManager, context)
            notificationManager.notify(WEBUI_NOTIFICATION_ID_BASE + downloadId, buildProgressNotification(context, displayName, 0))
        }
        var saved = false
        val job = scope.launch {
            var pendingUri: Uri? = null
            try {
                ensurePageActive(coroutineContext)
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(collection, values) ?: error("Failed to create download")
                pendingUri = uri
                source().use { input ->
                    val total = input.available().toLong()
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Failed to open download")
                    output.use { stream ->
                        var lastProgress = -1
                        writeWebUIDownload(input, stream) { written ->
                            ensurePageActive(coroutineContext)
                            val progress = if (total == 0L) 100 else ((written * 100L) / total).toInt().coerceIn(0, 100)
                            DownloadManager.updateProgress(downloadId, progress)
                            if (progress - lastProgress >= 2 || progress == 100) {
                                runCatching {
                                    notificationManager.notify(WEBUI_NOTIFICATION_ID_BASE + downloadId, buildProgressNotification(context, displayName, progress))
                                }
                                lastProgress = progress
                            }
                        }
                    }
                }
                ensurePageActive(coroutineContext)
                val published = android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                check(context.contentResolver.update(uri, published, null, null) == 1) { "Failed to publish download" }
                pendingUri = null
                saved = true
                DownloadManager.markCompleted(downloadId, uri)
                runCatching {
                    notificationManager.notify(WEBUI_NOTIFICATION_ID_BASE + downloadId, buildCompletionNotification(context, downloadId, displayName, uri, mimeType))
                }
                postToast(context.getString(R.string.download_complete_content, displayName))
            } catch (error: Exception) {
                pendingUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                if (error is CancellationException) throw error
                Log.e("WebUIDownload", "Failed to save $displayName", error)
                DownloadManager.markFailed(downloadId, error.message ?: "Download failed")
                runCatching { notificationManager.notify(WEBUI_NOTIFICATION_ID_BASE + downloadId, buildFailureNotification(context, displayName)) }
                postToast(context.getString(R.string.download_failed_content, displayName))
            }
        }
        job.invokeOnCompletion { error ->
            runCatching { onFinished() }
            if (error is CancellationException && !saved) {
                DownloadManager.markFailed(downloadId, "Cancelled")
                runCatching { notificationManager.cancel(WEBUI_NOTIFICATION_ID_BASE + downloadId) }
            }
        }
        return true
    }

    @JavascriptInterface
    fun startChunkedDownload(fileName: String?, mimeType: String?): String {
        if (!pageOpen()) { destroy(); return "" }
        val cacheDir = session?.cacheDir ?: webView?.context?.cacheDir ?: return ""
        val sanitizedFileName = sanitizeWebUIDownloadFileName(fileName)
        return runCatching {
            val id = blobs.registerBlobDownload(
                sanitizedFileName,
                mimeType,
                cacheDir,
            )
            if (pageOpen()) id else { blobs.cancelDownload(id); "" }
        }.getOrElse {
            Log.e("WebUIDownload", "Failed to start chunked download", it)
            ""
        }
    }

    @JavascriptInterface
    fun writeDownloadChunk(downloadId: String, base64Chunk: String): Boolean {
        if (!pageOpen()) { destroy(); return false }
        return blobs.writeChunk(downloadId, base64Chunk) && pageOpen()
    }

    @JavascriptInterface
    @Synchronized
    fun completeChunkedDownload(downloadId: String): Boolean {
        if (!pageOpen()) { destroy(); return false }
        if (!blobs.completeDownload(downloadId)) return false
        val download = blobs.takeDownload(downloadId) ?: return false
        val accepted = saveFile(download.fileName, download.mimeType, { download.input.close() }) { download.input }
        if (!accepted) download.input.close()
        return accepted
    }

    @JavascriptInterface
    fun cancelChunkedDownload(downloadId: String) {
        blobs.cancelDownload(downloadId)
    }

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        blobs.destroy()
        cleanupWebUiResources { scope.cancel() }
    }

    private fun postToast(message: String) {
        if (!pageOpen()) return
        webView?.let { currentWebView ->
            currentWebView.post {
                if (!pageOpen() || webView !== currentWebView) return@post
                Toast.makeText(currentWebView.context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensureNotificationChannel(notificationManager: NotificationManager, context: Context) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                DownloadService.CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildProgressNotification(
        context: Context,
        fileName: String,
        progress: Int,
    ) = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
        .setContentTitle(context.getString(R.string.download_progress_title, fileName))
        .setContentText("$progress%")
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setProgress(100, progress, progress == 0)
        .setOngoing(true)
        .setSilent(true)
        .build()

    private fun buildCompletionNotification(
        context: Context,
        downloadId: Int,
        fileName: String,
        contentUri: Uri,
        mimeType: String,
    ): android.app.Notification {
        val openIntent = Intent.createChooser(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            context.getString(R.string.open),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            downloadId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.download_complete_title))
            .setContentText(context.getString(R.string.download_complete_content, fileName))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, context.getString(R.string.open), pendingIntent)
            .build()
    }

    private fun buildFailureNotification(
        context: Context,
        fileName: String,
    ) = NotificationCompat.Builder(context, DownloadService.CHANNEL_ID)
        .setContentTitle(context.getString(R.string.download_failed_title))
        .setContentText(context.getString(R.string.download_failed_content, fileName))
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setAutoCancel(true)
        .build()
}
