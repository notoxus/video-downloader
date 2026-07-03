# Video Downloader for Android (standalone)

Standalone Android port of VideoDownloader. The app opens straight to Google — search or browse freely, exactly like opening a hunting browser on desktop. No need to paste a link first: play any video on the page and the built-in sniffer catches its HLS/DASH stream, then you pick MP4 or MP3 and it downloads **directly onto the phone** using an embedded yt-dlp + ffmpeg.

## Architecture (vs desktop)

| Desktop | Android |
|---|---|
| Chrome extension + local HTTP server | `WebView.shouldInterceptRequest()` — no extension, no server |
| yt-dlp.exe via ProcessBuilder | [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (bundled Python runtime) |
| ffmpeg.exe | youtubedl-android `ffmpeg` module (JNI) |
| Swing + FlatLaf | Jetpack Compose (Material 3) |

## Current status — working proof of concept

- ✅ In-app browser that opens on Google by default — search or browse freely, no link needed up front
- ✅ Address bar doubles as a search box: plain text becomes a Google search, URLs navigate directly
- ✅ **Direct-download shortcut**: a YouTube/TikTok/Facebook/Instagram link (typed, pasted, or shared in) skips
  hunting entirely and goes straight to the queue — mirrors the desktop app's `startHunting()` shortcut
- ✅ Live stream sniffing while browsing other sites (same URL patterns as the desktop extension)
- ✅ Format picker (MP4/MP3) per detected stream, with a sequential download queue (one at a time, like desktop)
- ✅ Per-item status (Detected → Queued → Downloading with progress → Done/Error) and remove/dismiss
- ✅ Accepts links from the Share sheet
- 🔲 Trim support (desktop parity)
- 🔲 Save to public `Movies/` via MediaStore (currently app-private storage)
- 🔲 Foreground service so downloads survive app switching

## Build

Open this folder in Android Studio and run, or:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Note: the APK is large (~70MB+) because it bundles a Python runtime for yt-dlp — the same trade-off the desktop app makes by bundling a JRE.

## Distribution

Google Play does not allow video-downloader apps; distribute the APK via GitHub Releases (sideload), like [Seal](https://github.com/JunkFood02/Seal) does.
