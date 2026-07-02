# Video Downloader for Android (standalone)

Standalone Android port of VideoDownloader: browse to a video page inside the app, play the video, and the built-in sniffer catches the HLS/DASH stream — then downloads it **directly onto the phone** using an embedded yt-dlp + ffmpeg.

## Architecture (vs desktop)

| Desktop | Android |
|---|---|
| Chrome extension + local HTTP server | `WebView.shouldInterceptRequest()` — no extension, no server |
| yt-dlp.exe via ProcessBuilder | [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (bundled Python runtime) |
| ffmpeg.exe | youtubedl-android `ffmpeg` module (JNI) |
| Swing + FlatLaf | Jetpack Compose (Material 3) |

## Current status — working proof of concept

- ✅ In-app browser with live stream sniffing (same URL patterns as the desktop extension)
- ✅ Direct download via embedded yt-dlp with progress bar
- ✅ Accepts links from the Share sheet
- 🔲 Download queue with multiple items (desktop parity)
- 🔲 Format picker (MP4/MP3) + trim support
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
