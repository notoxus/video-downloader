package com.videodownloader.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

enum class DownloadStatus { DETECTED, PENDING, DOWNLOADING, DONE, ERROR }

/** One row in the on-screen queue: a caught stream, from detection through to a finished download. */
class QueueItem(val id: Long, val url: String, val referer: String?) {
    var format by mutableStateOf<DownloadFormat?>(null)
    var status by mutableStateOf(DownloadStatus.DETECTED)
    var progress by mutableFloatStateOf(0f)
    var savedPath by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
}

/**
 * Tracks every stream the sniffer catches and downloads them one at a time —
 * mirrors the desktop app's single-worker BlockingQueue.
 */
class DownloadQueue(private val context: Context, scope: CoroutineScope) {
    private var nextId = 0L
    val items = mutableStateListOf<QueueItem>()
    private val channel = Channel<QueueItem>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            for (item in channel) {
                runDownload(item)
            }
        }
    }

    /** Called by the sniffer when a new stream URL is caught while browsing. */
    fun addDetected(url: String, referer: String?) {
        if (items.any { it.url == url }) return
        items.add(0, QueueItem(nextId++, url, referer))
    }

    /** User picked a format for a DETECTED item — sends it to the download worker. */
    fun startDownload(item: QueueItem, format: DownloadFormat) {
        item.format = format
        item.status = DownloadStatus.PENDING
        channel.trySend(item)
    }

    fun remove(item: QueueItem) {
        if (item.status == DownloadStatus.DOWNLOADING) return
        items.remove(item)
    }

    private suspend fun runDownload(item: QueueItem) {
        item.status = DownloadStatus.DOWNLOADING
        try {
            val dir = Downloader.download(context, item.url, item.referer, item.format!!) { percent, _ ->
                item.progress = percent
            }
            item.savedPath = dir.absolutePath
            item.status = DownloadStatus.DONE
        } catch (e: Exception) {
            item.errorMessage = e.message ?: "Unknown error"
            item.status = DownloadStatus.ERROR
        }
    }
}
