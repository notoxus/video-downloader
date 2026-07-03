package com.videodownloader.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15+ (targetSdk 35+) forces edge-to-edge by default, which draws our
        // content behind the status bar. Opt back into the classic layout so Scaffold's
        // own padding is enough and nothing renders under the system bars.
        WindowCompat.setDecorFitsSystemWindows(window, true)

        val sharedUrl = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.let { Regex("https?://\\S+").find(it)?.value }

        setContent {
            MaterialTheme {
                HunterScreen(initialUrl = sharedUrl ?: "")
            }
        }
    }
}

/** Turns free-form input into a navigable URL: pass URLs through, treat anything else as a Google search. */
private fun resolveNavigationTarget(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    return "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
}

/**
 * Platforms yt-dlp can extract directly from the page URL — no hunting needed.
 * Mirrors the desktop app's AppGUI.startHunting() shortcut list.
 */
private fun isDirectDownloadPlatform(url: String): Boolean {
    val lower = url.lowercase()
    return listOf("youtube.com", "youtu.be", "tiktok.com", "facebook.com", "instagram.com")
        .any { lower.contains(it) }
}

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
fun HunterScreen(initialUrl: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val queue = remember { DownloadQueue(context, scope) }

    // A shared/typed link to a platform yt-dlp already understands skips hunting entirely.
    val initialIsDirect = remember(initialUrl) { initialUrl.isNotBlank() && isDirectDownloadPlatform(initialUrl) }
    val huntStartUrl = remember(initialUrl) {
        if (initialIsDirect) "https://www.google.com" else initialUrl.ifBlank { "https://www.google.com" }
    }

    var addressText by remember { mutableStateOf(huntStartUrl) }
    var currentPage by remember { mutableStateOf("") }
    var status by remember {
        mutableStateOf("Browsing — play any video on this page to catch its stream.")
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // While true, WebView page-load callbacks must not touch `status` — covers the silent
    // background Google load that accompanies a direct-download detection, including any
    // redirects Google itself issues (which fire onPageFinished more than once). Cleared the
    // moment the user actually drives navigation themselves.
    var silentLoad by remember { mutableStateOf(initialIsDirect) }

    androidx.compose.runtime.LaunchedEffect(initialUrl) {
        if (initialIsDirect) {
            queue.addDetected(initialUrl, null)
            status = "Detected a YouTube/TikTok/Facebook/Instagram link — pick a format below."
        }
    }

    fun navigate(target: String) {
        val trimmed = target.trim()
        if (trimmed.startsWith("http") && isDirectDownloadPlatform(trimmed)) {
            silentLoad = true
            queue.addDetected(trimmed, null)
            status = "Detected a YouTube/TikTok/Facebook/Instagram link — pick a format below."
            return
        }
        silentLoad = false
        val url = resolveNavigationTarget(trimmed)
        currentPage = url
        status = "Loading… play the video to trigger detection."
        webViewRef?.loadUrl(url)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Search or paste a URL") }
                )
                Button(onClick = { navigate(addressText) }) { Text("Go") }
            }

            // Embedded browser with stream sniffing — starts on Google so you can search
            // and browse freely, exactly like opening a hunting browser on desktop.
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // WebView normally renders via its own hardware surface, which on some
                        // devices/emulators gets mis-ordered and paints over sibling Compose
                        // content. Forcing software rendering makes it participate normally
                        // in the view hierarchy's Z-order.
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        webViewClient = StreamSniffer(
                            onStreamFound = { streamUrl -> queue.addDetected(streamUrl, currentPage) },
                            onPageStarted = { pageUrl ->
                                // Only touch currentPage/addressText here — a silent background
                                // load (e.g. Google behind a direct-download detection) must not
                                // clobber a status message set elsewhere.
                                currentPage = pageUrl
                                addressText = pageUrl
                            },
                            onPageFinished = {
                                if (!silentLoad) {
                                    status = "Browsing — play any video on this page to catch its stream."
                                }
                            }
                        )

                        currentPage = huntStartUrl
                        loadUrl(huntStartUrl)
                        webViewRef = this
                    }
                }
            )

            Text(status, style = MaterialTheme.typography.bodySmall)

            Divider()
            Text("Detected streams & downloads", style = MaterialTheme.typography.titleSmall)

            LazyColumn(
                modifier = Modifier.weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (queue.items.isEmpty()) {
                    item {
                        Text(
                            "Nothing caught yet — browse to a video and press play.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                items(queue.items, key = { it.id }) { entry ->
                    QueueRow(entry, onPickFormat = { fmt -> queue.startDownload(entry, fmt) },
                        onRemove = { queue.remove(entry) })
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun QueueRow(item: QueueItem, onPickFormat: (DownloadFormat) -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(item.url, maxLines = 1, style = MaterialTheme.typography.bodySmall)

            when (item.status) {
                DownloadStatus.DETECTED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPickFormat(DownloadFormat.MP4) }) { Text("MP4") }
                        Button(onClick = { onPickFormat(DownloadFormat.MP3) }) { Text("MP3") }
                        TextButton(onClick = onRemove) { Text("Dismiss") }
                    }
                }
                DownloadStatus.PENDING -> {
                    Text("Queued…", style = MaterialTheme.typography.bodySmall)
                }
                DownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${item.format} — ${item.progress.toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                DownloadStatus.DONE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✅ Saved (${item.format})", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onRemove) { Text("Clear") }
                    }
                }
                DownloadStatus.ERROR -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("❌ ${item.errorMessage}", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onRemove) { Text("Clear") }
                    }
                }
            }
        }
    }
}
