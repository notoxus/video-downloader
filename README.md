# Video Downloader

A desktop app for capturing and downloading HLS/DASH video streams, with built-in support for YouTube, TikTok, Facebook, Instagram, and many more.

---

## Quick Start

### Windows
Extract the archive and double-click **`run.bat`**.

### macOS / Linux
```bash
chmod +x run.sh
./run.sh
```

No Java installation required — a bundled JRE is included.

---

## Features

| Feature | Description |
|---|---|
| **Browser Hunting** | Injects a Chrome extension that intercepts HLS/DASH stream URLs as you browse |
| **Direct Download** | YouTube, TikTok, Facebook, Instagram — extracted directly via yt-dlp |
| **Clipboard Monitor** | Background thread watches your clipboard; paste a URL and it auto-queues |
| **Bulk Import** | Import an API JSON with multiple episodes to queue an entire series |
| **Format Choice** | Download as MP4, MKV, or extract audio as MP3 |
| **Concurrent Fragments** | 16 parallel HLS fragment connections for fast stream downloads |

---

## How to Download a Video

### Method 1 — Direct URL (YouTube, TikTok, etc.)

1. Paste the video URL into the input field at the top.
2. Press **Enter** or click **Hunt / Download**.
3. The app detects the platform and queues the download automatically.

### Method 2 — Browser Hunting (streaming sites)

Many sites load video streams dynamically without a shareable URL. Hunting mode captures these.

1. Leave the input field **empty** and press **Enter**, or paste the site URL and press **Enter**.
2. A dedicated Chrome window opens and navigates to the page.
3. Play the video — the extension intercepts the stream URL (`.m3u8`, DASH, or any `application/x-mpegURL` response).
4. The tab closes automatically and the download starts.

> **First run only:** Chrome will ask you to enable Developer Mode and load the extension. Follow the on-screen instructions — this is a one-time step.

### Method 3 — Clipboard Monitor

Just copy a video URL from anywhere. The app detects it and adds it to the queue within 1–2 seconds.

### Method 4 — Bulk API JSON Import

Click **Import API JSON** and paste a payload in this format:

```json
{
  "movie": { "name": "Series Title" },
  "episodes": [
    {
      "items": [
        { "name": "Episode 1", "m3u8": "https://cdn.example.com/ep1/playlist.m3u8" },
        { "name": "Episode 2", "m3u8": "https://cdn.example.com/ep2/playlist.m3u8" }
      ]
    }
  ]
}
```

All episodes are queued at once. Select them in the table and click **Download Selected**.

---

## Download Queue

The queue table shows all pending and active downloads:

| Column | Meaning |
|---|---|
| **Ord No.** | Position in queue |
| **Link** | Source URL |
| **Format** | MP4 / MKV / MP3 |
| **Status** | Pending / In Queue / Downloading / Done / Error |
| **Progress** | Live download percentage |
| **×** | Remove from queue (cannot remove active downloads) |

Select one or more rows and click **Download Selected** to start them.

---

## How Hunting Works (Technical)

The app generates a Chrome extension at `~/.VideoDownloaderApp/Extension/` and loads it into a dedicated Chrome profile. The extension hooks into Chrome's `webRequest` API with two interception layers:

1. **URL Pattern Matching** — fires before each request and checks for `.m3u8`, `.mpd`, HLS query parameters (`format=m3u8`, `type=hls`, etc.), and common path segments (`/hls/`, `/dash/`, `/manifest`).

2. **Content-Type Sniffing** — fires when response headers arrive and checks the `Content-Type` for `application/x-mpegURL`, `application/vnd.apple.mpegurl`, or `application/dash+xml`. This catches streams served from URLs with no file extension.

Captured URLs are sent via HTTP POST to the app on `localhost:8765`, then queued for download.

---

## Requirements

- **OS:** Windows 10+, macOS 12+, or Linux (x64/ARM)
- **Browser:** Google Chrome or Chromium (for Hunting mode)
- **Internet:** Required for downloading dependencies on first run (yt-dlp, ffmpeg)

---

## Building from Source

Requirements: Java 21, Maven 3.3+

```bash
mvn clean package
```

Outputs are in `target/` — platform-specific archives for Windows, macOS (x64/ARM), and Linux (x64/ARM).

---

## Troubleshooting

**The extension tab doesn't close / nothing gets captured**
- Make sure the app is running before you open the capture browser.
- Check that port 8765 is not blocked by a firewall.
- Some sites use DRM (Widevine) — encrypted streams cannot be downloaded.

**Download fails with an error**
- Update yt-dlp: delete `yt-dlp.exe` (or `yt-dlp`) from the app folder and restart — it will auto-download the latest version.
- Some sites require cookies. Open the site normally in Chrome (logged in), then use Hunting mode.

**Chrome says the extension is invalid**
- Delete `~/.VideoDownloaderApp/Extension/` and restart the app to regenerate it.
