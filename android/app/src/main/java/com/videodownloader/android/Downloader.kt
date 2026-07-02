package com.videodownloader.android

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
     * Downloads [url] as mp4 into the app's download folder.
     * [referer] helps with sites that check the Referer header.
     * Progress is reported as (percent, etaSeconds).
     */
    suspend fun download(
        context: Context,
        url: String,
        referer: String?,
        onProgress: (Float, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        init(context)
        val dir = downloadDir(context)

        val request = YoutubeDLRequest(url).apply {
            addOption("-o", File(dir, "%(title)s.%(ext)s").absolutePath)
            addOption("-f", "best")
            addOption("--no-mtime")
            addOption("-N", "8")
            if (!referer.isNullOrBlank()) {
                addOption("--add-header", "Referer: $referer")
            }
        }

        YoutubeDL.getInstance().execute(request) { progress, etaSeconds, _ ->
            onProgress(progress, etaSeconds)
        }
        dir
    }
}
