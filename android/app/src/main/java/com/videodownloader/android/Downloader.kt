package com.videodownloader.android

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
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

            // The bundled yt-dlp is frozen at the app's build date. YouTube frequently
            // changes its API in ways that break older extractors (e.g. "ERROR -
            // Precondition check failed"), so pull the latest yt-dlp release on first use —
            // mirrors the desktop app's yt-dlp -U self-update. Best-effort: if there's no
            // network, downloads just fall back to whatever version shipped with the APK.
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            } catch (e: Exception) {
                // Ignore — proceed with the bundled version.
            }
        }
    }

    private fun workDir(context: Context): File =
        File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }

    /**
     * Downloads [url] in the requested [format], then publishes the file into the phone's public
     * Movies/Music library so it shows up in the Gallery and file managers.
     * Returns a human-readable saved location (e.g. "Movies/VideoDownloader/Title.mp4").
     */
    suspend fun download(
        context: Context,
        url: String,
        referer: String?,
        format: DownloadFormat,
        onProgress: (Float, Long) -> Unit
    ): String = withContext(Dispatchers.IO) {
        init(context)
        val dir = workDir(context)
        val before = dir.listFiles()?.map { it.name }?.toHashSet() ?: hashSetOf()

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

        val response = YoutubeDL.getInstance().execute(request) { progress, etaSeconds, _ ->
            onProgress(progress, etaSeconds)
        }

        val file = locateOutput(dir, before, response.out)
            ?: throw IllegalStateException("Download finished but the output file couldn't be found.")

        exportToPublic(context, file, format)
    }

    /** Finds the file yt-dlp actually produced, from its stdout or (as a fallback) the newest new file. */
    private fun locateOutput(dir: File, before: Set<String>, stdout: String): File? {
        val patterns = listOf(
            Regex("""\[Merger] Merging formats into "(.+)""""),
            Regex("""\[ExtractAudio] Destination: (.+)"""),
            Regex("""\[download] Destination: (.+)"""),
            Regex("""\[download] (.+) has already been downloaded""")
        )
        for (line in stdout.lines().asReversed()) {
            for (p in patterns) {
                val m = p.find(line.trim())
                if (m != null) {
                    val f = File(m.groupValues[1].trim())
                    if (f.exists()) return f
                }
            }
        }
        // Fallback: newest non-temporary file that wasn't in the folder before this download.
        return dir.listFiles()
            ?.filter { it.isFile && it.name !in before }
            ?.filterNot { it.name.endsWith(".part") || it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.lastModified() }
    }

    /**
     * Copies [file] into the shared media library. On API 29+ this uses MediaStore (no storage
     * permission needed) so the file appears in Gallery/Files under Movies/ or Music/. On older
     * versions it stays in app storage (a public copy there needs a runtime storage permission).
     */
    private fun exportToPublic(context: Context, file: File, format: DownloadFormat): String {
        val isAudio = format == DownloadFormat.MP3
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return file.absolutePath
        }

        val resolver = context.contentResolver
        val collection = if (isAudio)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val relativePath = if (isAudio) "Music/VideoDownloader" else "Movies/VideoDownloader"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio) "audio/mpeg" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return file.absolutePath
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        file.delete() // remove the private working copy now that it's in the public library
        return "$relativePath/${file.name}"
    }
}
