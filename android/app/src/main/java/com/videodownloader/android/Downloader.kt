package com.videodownloader.android

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class DownloadFormat { MP4, MP3 }

/** Wraps the embedded yt-dlp. Mirrors the desktop NeccessaryToolsAdapter. */
object Downloader {

    @Volatile
    private var initialized = false

    /** Must be called once before downloading. Safe to call repeatedly. */
    suspend fun init(context: Context) {
        if (initialized) return
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().init(context)
            initialized = true
        }
    }

    fun downloadDir(context: Context): File =
        File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }

    /**
     * Downloads [url] into the app's download folder in the requested [format].
     * [referer] helps with sites that check the Referer header.
     * Progress is reported as (percent, etaSeconds).
     */
    suspend fun download(
        context: Context,
        url: String,
        referer: String?,
        format: DownloadFormat,
        onProgress: (Float, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        init(context)
        val dir = downloadDir(context)

        val request = YoutubeDLRequest(url).apply {
            addOption("-o", File(dir, "%(title)s.%(ext)s").absolutePath)
            addOption("--no-mtime")
            addOption("-N", "8")
            if (!referer.isNullOrBlank()) {
                addOption("--add-header", "Referer: $referer")
            }
            when (format) {
                DownloadFormat.MP4 -> {
                    addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best")
                    addOption("--merge-output-format", "mp4")
                }
                DownloadFormat.MP3 -> {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                }
            }
        }

        YoutubeDL.getInstance().execute(request) { progress, etaSeconds, _ ->
            onProgress(progress, etaSeconds)
        }
        dir
    }
}
