package com.videodownloader.android

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * WebViewClient that inspects every sub-request the page makes and reports
 * HLS/DASH manifest URLs — the Android equivalent of the desktop Chrome
 * extension, but with no extension setup required.
 */
class StreamSniffer(
    private val onStreamFound: (String) -> Unit,
    private val onPageStarted: (String) -> Unit = {},
    private val onPageFinished: (String) -> Unit = {}
) : WebViewClient() {

    private val seen = HashSet<String>()

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinished(url)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (looksLikeStream(url) && seen.add(url)) {
            // Called on a background thread; hop to main for UI updates.
            view.post { onStreamFound(url) }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun looksLikeStream(rawUrl: String): Boolean {
        val url = rawUrl.lowercase()

        // Direct extension check
        if (url.contains(".m3u8") || url.contains(".mpd")) {
            return !url.contains("audio-only") && !url.contains("/preview")
        }

        // Query-parameter patterns used by sites that hide stream URLs
        val queryPatterns = listOf(
            "format=m3u8", "type=m3u8", "format=hls", "type=hls",
            "output=m3u8", "output=hls", "stream_type=hls",
            "container=m3u8", "protocol=hls",
            "format=mpd", "type=mpd", "type=dash"
        )
        if (queryPatterns.any { url.contains(it) }) return true

        // Path-segment patterns
        val pathPatterns = listOf(
            "/hls/", "/dash/", "/manifest", "/chunklist",
            "master.m3u8", "media.m3u8", "index.m3u8", "playlist.m3u8"
        )
        return pathPatterns.any { url.contains(it) }
    }
}
