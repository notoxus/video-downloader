# Video Downloader

![Downloads](https://img.shields.io/github/downloads/notoxus/video-downloader/total)

[How to install the App](Installation.md)

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
| **Browser Hunting** | Intercepts HLS/DASH stream URLs as you browse via Chrome/Chromium or Mozilla Firefox extension |
| **Direct Download** | YouTube, TikTok, Facebook, Instagram — extracted directly via yt-dlp |
| **Clipboard Monitor** | Background thread watches your clipboard; paste a URL and it auto-queues |
| **Bulk Import** | Import an API JSON with multiple episodes to queue an entire series |
| **Format Choice** | Download as MP4, MKV, or extract audio as MP3 |
| **Concurrent Fragments** | 16 parallel HLS fragment connections for fast stream downloads |
| **Video Titles** | Queue shows the actual video title instead of the raw URL (hover for the full link) |
| **Search & Clear** | Filter the queue with the search bar; clear all pending items in one click |
| **Auto-Update (engine)** | yt-dlp self-updates in the background on every launch, so site extractors stay fresh |
| **Auto-Update (app)** | On launch the app checks GitHub for a newer release and can download the ready-to-use package for your exact OS/architecture |
| **Trim Before Download** | Cut a specific section (e.g. 01:30 → 02:45) and download only that clip — no full download needed |

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

### Trimming a Clip

When the format dialog appears, tick **"Cut a section before downloading"**. The app reads the video length and shows a **dual-handle slider over a thumbnail filmstrip** — just drag the two handles to set the start and end. The live label shows the exact times and the resulting clip length.

- **Fast cut (default):** cuts on the nearest keyframe using a stream copy — nearly as quick as a normal download.
- **Frame-accurate cut:** tick "Frame-accurate cut" to start/end at the exact second. This re-encodes the clip, so it's slower but precise.

Notes:
- The filmstrip preview is best-effort. For DRM-protected or some login-gated streams it may not appear — the slider still works.
- The queue shows a "trim" mark next to the format for trimmed downloads and you can also remove it with "remove" mark.

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
- **Browser:** Google Chrome, Chromium, Brave, Microsoft Edge, or Mozilla Firefox (for Hunting mode)
- **Internet:** Required on first launch only if the bundled JRE is missing (auto-downloaded from Adoptium)

---

## Building from Source

Requirements: Java 21, Maven 3.3+

```bash
mvn clean package
```

Outputs are in `target/` — platform-specific archives for Windows, macOS (x64/ARM), and Linux (x64/ARM).

> **Note:** The release archives bundle a trimmed JRE built automatically by CI (`jlink`). When building locally, the `tools/jre-*/` directories must be present for the assembly to include them. The launchers (`run.bat` / `run.sh`) will fall back to auto-downloading a JRE from Adoptium if the folder is missing.

---

## Git Tag & Release Management

The GitHub Actions CI automatically builds multi-platform packages and publishes a GitHub Release whenever a new tag matching `v*.*.*` is pushed.

### 1. Create and Push a New Tag (Release)

Use the `./bump-version.sh` script to automatically bump the version and synchronize all project files:

```bash
# Auto-bump patch version from the latest tag (e.g. v1.0.6 -> v1.0.7):
./bump-version.sh

# Or specify an explicit version:
./bump-version.sh [new version]
# For example:
./bump-version.sh 1.0.7

# Commit the version changes and create the tag:
git commit -am "release: v1.0.7"
git tag v1.0.7

# Push code and tag to GitHub to trigger CI release build:
git push origin main
git push origin v1.0.7
```

### 2. Delete a Tag

When you need to delete an incorrect or broken tag:

- **Delete local tag:**
  ```bash
  git tag -d v1.0.7
  ```
- **Delete remote tag on GitHub:**
  ```bash
  git push origin --delete v1.0.7
  ```
- *(Optional)* If GitHub already created a Release for that tag, go to **Releases** on GitHub and click **Delete release**.

### 3. Overwrite / Force Update a Tag

When you need to update an existing tag to point to a newer commit without changing the version number:

```bash
# 1. Update the local tag to point to the current commit (using -f / --force):
git tag -f v1.0.7

# 2. Force-push the updated tag to GitHub:
git push origin -f v1.0.7
```

---

## Troubleshooting

**The extension tab doesn't close / nothing gets captured**
- Make sure the app is running before you open the capture browser.
- Check that port 8765 is not blocked by a firewall.
- Some sites use DRM (Widevine) — encrypted streams cannot be downloaded.

**Download fails with an error**
- Update yt-dlp: delete `yt-dlp.exe` (or `yt-dlp`) from the app folder and restart — it will auto-download the latest version.
- Some sites require cookies. Open the site normally in browser (logged in), then use Hunting mode.

**Browser says the extension is invalid**
- Delete `~/.VideoDownloaderApp/Extension/` and restart the app to regenerate it.

**yt-dlp warns "No supported JavaScript runtime could be found"**
- YouTube now requires a JavaScript runtime for full format extraction. Install [Deno](https://deno.land):
  - Windows: `winget install DenoLand.Deno`
  - macOS/Linux: `curl -fsSL https://deno.land/install.sh | sh`
- Downloads still work without it, but some YouTube formats may be missing.
