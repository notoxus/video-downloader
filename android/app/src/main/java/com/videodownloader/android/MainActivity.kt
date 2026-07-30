package com.videodownloader.android

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    // Incoming shared URL, exposed to Compose. singleTask (see manifest) keeps the whole app in
    // one instance: a share from another app is delivered to onNewIntent on the existing activity
    // instead of spawning a duplicate task. `seq` bumps so the same URL shared twice still fires.
    private var incomingUrl by mutableStateOf<String?>(null)
    private var incomingSeq by mutableIntStateOf(0)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15+ (targetSdk 35+) forces edge-to-edge by default, which draws our
        // content behind the status bar. Opt back into the classic layout so Scaffold's
        // own padding is enough and nothing renders under the system bars.
        WindowCompat.setDecorFitsSystemWindows(window, true)

        consumeShareIntent(intent)

        setContent {
            MaterialTheme {
                HunterScreen(incomingUrl = incomingUrl, incomingSeq = incomingSeq)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    private fun consumeShareIntent(intent: Intent?) {
        val url = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.let { Regex("https?://\\S+").find(it)?.value }
        if (url != null) {
            incomingUrl = url
            incomingSeq++
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

private const val HOME_URL = "https://www.google.com"

// A modern mobile Chrome UA so sites (Google, YouTube, etc.) serve their tappable mobile layout
// instead of the cramped desktop one the default WebView UA can trigger.
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
fun HunterScreen(incomingUrl: String?, incomingSeq: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val queue = remember { DownloadQueue(context, scope) }

    var addressText by remember { mutableStateOf(HOME_URL) }
    var currentPage by remember { mutableStateOf(HOME_URL) }
    var status by remember {
        mutableStateOf("Browsing — play any video on this page to catch its stream.")
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastClipboard by remember { mutableStateOf("") }

    // When true, the WebView's page-load callback must not overwrite a "Detected/Added" message.
    // Set whenever a direct-download link is queued (those never navigate the WebView); cleared
    // the moment the user actually navigates somewhere to hunt. Initialised from the launch intent
    // so the first silent Google load doesn't clobber a shared link's status.
    val initialDirect = remember {
        incomingSeq > 0 && incomingUrl != null && isDirectDownloadPlatform(incomingUrl)
    }
    var suppressBrowsingStatus by remember { mutableStateOf(initialDirect) }

    fun queueDirect(url: String, message: String) {
        suppressBrowsingStatus = true
        queue.addDetected(url, null)
        status = message
    }

    fun routeUrl(raw: String, fromUser: Boolean) {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http") && isDirectDownloadPlatform(trimmed)) {
            queueDirect(trimmed, "Detected a YouTube/TikTok/Facebook/Instagram link — pick a format below.")
            return
        }
        if (fromUser) {
            suppressBrowsingStatus = false
            val url = resolveNavigationTarget(trimmed)
            currentPage = url
            status = "Loading… play the video to trigger detection."
            webViewRef?.loadUrl(url)
        }
    }

    // Share intents (initial launch + onNewIntent while running) — all handled in this one session.
    LaunchedEffect(incomingSeq) {
        val url = incomingUrl
        if (incomingSeq > 0 && url != null) {
            routeUrl(url, fromUser = false)
        }
    }

    // Clipboard watch: Android only lets apps read the clipboard while in the foreground, so we
    // check on resume rather than poll in the background like the desktop app does. A freshly
    // copied YouTube/TikTok/… link is auto-added to the queue when you return to the app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            ?.coerceToText(context)?.toString().orEmpty()
        val url = Regex("https?://\\S+").find(text)?.value
        if (url != null && url != lastClipboard && isDirectDownloadPlatform(url)) {
            lastClipboard = url
            queueDirect(url, "Added a link from your clipboard — pick a format below.")
        }
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Search or paste a URL") }
                )
                Button(onClick = { routeUrl(addressText, fromUser = true) }) { Text("Go") }
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
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = MOBILE_UA

                        webViewClient = StreamSniffer(
                            onStreamFound = { streamUrl -> queue.addDetected(streamUrl, currentPage) },
                            onPageStarted = { pageUrl ->
                                currentPage = pageUrl
                                addressText = pageUrl
                            },
                            onPageFinished = {
                                if (!suppressBrowsingStatus) {
                                    status = "Browsing — play any video on this page to catch its stream."
                                }
                            }
                        )

                        currentPage = HOME_URL
                        loadUrl(HOME_URL)
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
                    Text("✅ Saved to ${item.savedPath ?: item.format.toString()}",
                        maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onRemove) { Text("Clear") }
                }
                DownloadStatus.ERROR -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("❌ ${item.errorMessage}", maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onRemove) { Text("Clear") }
                    }
                }
            }
        }
    }
}
