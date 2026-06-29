package com.videodownloader.controller;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public class ExtensionManager {

    // Bump this version to force-regenerate the extension on existing installs.
    private static final String EXTENSION_VERSION = "1.2";

    public static String getExtensionPath() {
        String extDir = System.getProperty("user.home") + File.separator + ".VideoDownloaderApp"
                + File.separator + "Extension";
        File dir = new File(extDir);
        File versionFile = new File(extDir, ".version");

        boolean needsRegen = !dir.exists()
                || !versionFile.exists()
                || !readFile(versionFile).equals(EXTENSION_VERSION);

        if (needsRegen) {
            dir.mkdirs();
            System.out.println("[ExtensionManager] Generating extension v" + EXTENSION_VERSION + "...");
            try {
                writeFile(new File(extDir, "manifest.json"), buildManifest());
                writeFile(new File(extDir, "background.js"), buildBackground());
                writeFile(versionFile, EXTENSION_VERSION);
            } catch (Exception e) {
                System.err.println("[ExtensionManager] Failed to write extension: " + e.getMessage());
            }
        }
        return extDir;
    }

    private static String readFile(File f) {
        try {
            return Files.readString(f.toPath()).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeFile(File f, String content) throws IOException {
        try (FileWriter fw = new FileWriter(f)) {
            fw.write(content);
        }
    }

    private static String buildManifest() {
        return """
                {
                  "manifest_version": 3,
                  "name": "Video Hunter (Java Bridge)",
                  "version": "%s",
                  "permissions": ["webRequest", "tabs"],
                  "host_permissions": ["<all_urls>"],
                  "background": { "service_worker": "background.js" }
                }
                """.formatted(EXTENSION_VERSION);
    }

    private static String buildBackground() {
        return """
                const CAPTURE_URL = 'http://localhost:8765/capture';
                const seen = new Set();

                function sendCapture(url, tabId) {
                    if (seen.has(url)) return;
                    seen.add(url);
                    console.log('[Hunter] Captured:', url);
                    fetch(CAPTURE_URL, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ url: url })
                    })
                    .then(r => {
                        if (r.ok && tabId != null && tabId >= 0) {
                            setTimeout(() => chrome.tabs.remove(tabId), 800);
                        }
                    })
                    .catch(() => console.log('[Hunter] Local server is offline'));
                }

                // ── Listener 1: URL pattern matching (fires before the request leaves) ──────
                function looksLikeStream(url) {
                    const lower = url.toLowerCase();

                    // Direct file-extension check
                    if (lower.includes('.m3u8') || lower.includes('.m3u') || lower.includes('.mpd')) {
                        return !lower.includes('audio-only') && !lower.includes('/preview');
                    }

                    // Query-parameter patterns used by sites that hide stream URLs
                    const qParams = [
                        'format=m3u8', 'type=m3u8', 'format=hls', 'type=hls',
                        'output=m3u8', 'output=hls', 'stream_type=hls',
                        'container=m3u8', 'protocol=hls',
                        'format=mpd', 'type=mpd', 'type=dash'
                    ];
                    if (qParams.some(p => lower.includes(p))) return true;

                    // Path-segment patterns
                    const paths = [
                        '/hls/', '/dash/', '/manifest', '/chunklist',
                        'master.m3u8', 'media.m3u8', 'stream.m3u8',
                        'video.m3u8', 'index.m3u8', 'live.m3u8',
                        'vod.m3u8', 'playlist.m3u8'
                    ];
                    return paths.some(p => lower.includes(p));
                }

                chrome.webRequest.onBeforeRequest.addListener(
                    function(details) {
                        if (looksLikeStream(details.url)) {
                            sendCapture(details.url, details.tabId);
                        }
                    },
                    { urls: ['<all_urls>'], types: ['xmlhttprequest', 'media', 'other', 'sub_frame'] }
                );

                // ── Listener 2: Content-Type sniffing (catches streams with no .m3u8 in URL) ─
                chrome.webRequest.onHeadersReceived.addListener(
                    function(details) {
                        if (!details.responseHeaders) return;
                        const ctHeader = details.responseHeaders.find(
                            h => h.name.toLowerCase() === 'content-type'
                        );
                        if (!ctHeader) return;
                        const ct = ctHeader.value.toLowerCase();
                        const streamMimeTypes = [
                            'application/x-mpegurl',
                            'application/vnd.apple.mpegurl',
                            'application/dash+xml'
                        ];
                        if (streamMimeTypes.some(t => ct.includes(t))) {
                            sendCapture(details.url, details.tabId);
                        }
                    },
                    { urls: ['<all_urls>'], types: ['xmlhttprequest', 'media', 'other', 'sub_frame'] },
                    ['responseHeaders']
                );
                """;
    }
}
