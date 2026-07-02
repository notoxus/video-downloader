# Video Downloader Companion (Android)

A tiny Android app that adds **"Send to PC"** to the Android Share sheet. Share any video link from YouTube, TikTok, or a browser — it's forwarded over your Wi-Fi to the desktop VideoDownloader app, which downloads it on the PC.

## How it works

```
Phone (Share → Send to PC)  --Wi-Fi POST :8765/add-->  Desktop VideoDownloader
```

The desktop app (v1.0.3+) listens on port 8765 on all interfaces and prints its LAN address in the console log at startup, e.g.:

```
[Companion] Phone companion can reach this PC at: http://192.168.1.10:8765
```

## Setup

1. Build & install the APK (see below), or grab it from Releases.
2. Open the app once → enter the PC's IP from the log line above → **Save & Test**.
3. From now on, just use the Share button in any app → **Send to PC**.

Requirements: phone and PC on the same Wi-Fi, desktop app running. On the first connection Windows Firewall may ask to allow Java — accept it.

---

## Hướng dẫn tìm địa chỉ IP của máy tính

Điện thoại cần biết địa chỉ IP của máy tính trong mạng Wi-Fi để gửi link về. Có 2 cách:

### Cách 1 — Xem trong app Video Downloader (dễ nhất, khuyên dùng)

1. Mở app **Video Downloader** trên máy tính.
2. Nhìn vào khung **Console Log** ở dưới cùng app.
3. Tìm dòng có dạng:
   ```
   [Companion] Phone companion can reach this PC at: http://192.168.1.10:8765
   ```
4. Số ở giữa `http://` và `:8765` chính là địa chỉ IP cần nhập vào app trên điện thoại (ví dụ trên là `192.168.1.10`).

Nếu không thấy dòng này, kéo lên đầu console log — nó chỉ in ra **một lần** ngay lúc mở app.

### Cách 2 — Dùng lệnh `ipconfig` (nếu Cách 1 không tìm thấy)

Dùng khi bạn không mở được app, hoặc muốn tự kiểm tra lại địa chỉ IP.

**Trên Windows:**

1. Bấm tổ hợp phím **Windows + R** để mở hộp thoại Run.
2. Gõ `cmd` rồi nhấn **Enter** — cửa sổ đen (Command Prompt) sẽ hiện ra.
3. Gõ lệnh sau rồi nhấn **Enter**:
   ```
   ipconfig
   ```
4. Bạn sẽ thấy một đoạn kết quả dài, tìm phần có tên **"Wireless LAN adapter Wi-Fi"** (nếu máy dùng Wi-Fi) hoặc **"Ethernet adapter"** (nếu cắm dây mạng LAN). Bỏ qua các phần khác như "Bluetooth" hay "VPN".
5. Trong phần đó, tìm dòng **IPv4 Address** — đây chính là địa chỉ IP cần dùng:
   ```
   Wireless LAN adapter Wi-Fi:

      Connection-specific DNS Suffix  . :
      Link-local IPv6 Address . . . . . : fe80::...
      IPv4 Address. . . . . . . . . . . : 192.168.1.10   <-- Đây, chỉ lấy số này
      Subnet Mask . . . . . . . . . . . : 255.255.255.0
      Default Gateway . . . . . . . . . : 192.168.1.1
   ```
6. Nhập đúng dãy số đó (ví dụ `192.168.1.10`) vào ô IP trong app trên điện thoại rồi bấm **Save & Test**.

**Trên macOS:**

1. Mở **System Settings → Wi-Fi** → bấm vào mạng Wi-Fi đang kết nối → **Details** → xem dòng **IP Address**.
2. Hoặc mở **Terminal** và gõ: `ipconfig getifaddr en0`

**Trên Linux:**

1. Mở Terminal và gõ: `hostname -I` hoặc `ip addr show` rồi tìm dòng `inet` trong phần card Wi-Fi (thường tên `wlan0` hoặc `wlp...`).

### Lưu ý quan trọng

- Địa chỉ IP có thể **đổi mỗi khi khởi động lại router hoặc kết nối lại Wi-Fi**. Nếu app điện thoại báo lỗi không kết nối được sau một thời gian, hãy kiểm tra lại IP bằng Cách 1 hoặc Cách 2 ở trên.
- Điện thoại và máy tính phải **cùng một mạng Wi-Fi** (không phải điện thoại dùng 4G/5G còn máy tính dùng Wi-Fi khác).
- Nếu vẫn không kết nối được, kiểm tra xem **Windows Firewall** có đang chặn Java không (Cài đặt → Windows Security → Firewall → Allow an app).

## Build

Open this folder in Android Studio, or:

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
