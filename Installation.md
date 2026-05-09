# 🚀 Installation Guide

Welcome to the setup guide for **Video Downloader**! The application is now fully **Plug & Play**, meaning you **do not need to install Java** or configure any environment variables on your system. Everything required to run the app is completely self-contained.

## Step 1: Requirements
Before running the application, ensure your system meets the following minor requirements:
* **Google Chrome:** Required specifically if you want to use the browser extension "Hunting" mode.
* **No Java installation needed!** A pre-configured Java 21 Runtime Environment (JRE) is already embedded inside the package.

## Step 2: Download the App
1. Navigate to the **[Releases](../../releases)** page of this repository.
2. Download the compressed archive that matches your Operating System:
   * **Windows:** Download `VideoDownloader-v1.0.2-Win.zip`
   * **MacOS:**
    	* Download `VideoDownloader-v1.0.2-Mac-Intel.tar.gz` (Supporting to Mac's x64 architecture)
    	* Download `VideoDownloader-v1.0.2-Mac-AppleSilicon.tar.gz` (Supporting to Mac's ARM architecture)
   * **Linux:**
   		* Download `VideoDownloader-v1.0.2-Linux-x64.tar.gz` (Supporting to x64 architecture too)
     	* Download `VideoDownloader-v1.0.2-Linux-ARM.tar.gz` (Supporting to ARM architecture too)

## 📂 Package Structure (What's Inside)
Once extracted, your installation folder will contain a clean, pre-configured ecosystem:
* ⚙️ **Core App:** `VideoDownloader.jar` (The main compiled application).
* 🚀 **Launcher Script:** `run.bat` (Windows) or `run.sh` (Mac/Linux) used to trigger the application.
* ☕ **Embedded Runtime:** Pre-bundled isolated JRE 21 environments tailored specifically for your platform architecture.
* 🛠️ **Engine Tools:** Built-in binaries (`yt-dlp`, `ffmpeg`, `node`) required natively for streaming, downloading, and converting.

---

## Step 3: Run the Application

### For Windows Users
1. Extract the downloaded `VideoDownloader-v1.0.2-Win.zip` archive to any folder of your choice.
2. Simply **double-click** the **`run.bat`** file to launch the app!
   *(Note: A background launcher ensures the app opens seamlessly without leaving an annoying black command prompt window open).*

### For macOS & Linux Users
Unix-based systems require explicit permission to execute launcher scripts.
1. Extract the downloaded `.tar.gz` archive.
2. Open your **Terminal** and navigate to the extracted directory:
   ```bash
   cd /path/to/extracted/folder
   ```
3. Grant execution permission to the launcher script by running:

	```bash
	chmod +x run.sh
	```
4. Launch the application simply by executing:

	```bash
	./run.sh
	```
