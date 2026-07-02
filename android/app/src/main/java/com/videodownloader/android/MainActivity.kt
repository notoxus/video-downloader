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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

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

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
fun HunterScreen(initialUrl: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf(initialUrl) }
    var currentPage by remember { mutableStateOf("") }
    val foundStreams = remember { mutableListOf<String>().toMutableStateList() }
    var status by remember { mutableStateOf("Enter a URL and press Go — play the video to hunt its stream.") }
    var progress by remember { mutableFloatStateOf(-1f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Video page URL") }
                )
                Button(onClick = {
                    val target = urlText.trim()
                    if (target.startsWith("http")) {
                        currentPage = target
                        foundStreams.clear()
                        status = "Loading page… play the video to trigger detection."
                        webViewRef?.loadUrl(target)
                    }
                }) { Text("Go") }
            }

            // Embedded browser with stream sniffing
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = StreamSniffer { streamUrl ->
                            if (!foundStreams.contains(streamUrl)) {
                                foundStreams.add(streamUrl)
                                status = "🎯 Found ${foundStreams.size} stream(s)!"
                            }
                        }
                        if (initialUrl.startsWith("http")) {
                            currentPage = initialUrl
                            loadUrl(initialUrl)
                        }
                        webViewRef = this
                    }
                }
            )

            Text(status, style = MaterialTheme.typography.bodySmall)
            if (progress in 0f..100f) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(foundStreams) { stream ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                stream,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(onClick = {
                                status = "Downloading…"
                                progress = 0f
                                scope.launch {
                                    try {
                                        val dir = Downloader.download(
                                            context, stream, currentPage
                                        ) { p, _ -> progress = p }
                                        progress = -1f
                                        status = "✅ Saved to ${dir.absolutePath}"
                                    } catch (e: Exception) {
                                        progress = -1f
                                        status = "❌ ${e.message}"
                                    }
                                }
                            }) { Text("Download") }
                        }
                    }
                }
            }
        }
    }
}
