package com.videodownloader.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DependencyManager {

	public static void checkAndDownloadDependencies() {
		String os = System.getProperty("os.name").toLowerCase();
		String arch = System.getProperty("os.arch").toLowerCase();
		String dir = System.getProperty("user.dir");
		String ytDlpName = "";
		String fFmpegName = "";

		if (os.contains("win")) {
			// windows
			ytDlpName = "yt-dlp.exe";
			fFmpegName = "ffmpeg.exe";
		} else if (os.contains("mac")) {
			// mac os
			ytDlpName = "yt-dlp_macos";
			if (arch.contains("aarch64") || arch.contains("arm")) {
				// Mac with apple silicon chipset, using arm architecture
				fFmpegName = "ffmpeg-darwin-arm64";
			} else {
				// Mac intel chipset
				fFmpegName = "ffmpeg-darwin-x64";
			}
		} else {
			// Same to linux
			if (arch.contains("aarch64") || arch.contains("arm")) {
				ytDlpName = "yt-dlp_linux_aarch64";
				fFmpegName = "ffmpeg-linux-arm64";
			} else {
				ytDlpName = "yt-dlp_linux";
				fFmpegName = "ffmpeg-linux-x64";
			}
		}

		File ytDlpFile = new File(dir, ytDlpName);
		File fFmpegFile = new File(dir, fFmpegName);
		if (!ytDlpFile.exists()) {
			System.out.println("yt-dlp installing...");
			try {
				String downloadUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/" + ytDlpName;
				InputStream in = new URI(downloadUrl).toURL().openStream();
				Files.copy(in, ytDlpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				ytDlpFile.setExecutable(true);
				System.out.println("yt-dlp installation complete!");
			} catch (Exception e) {
				System.err.println("Can not install yt-dlp: " + e.getMessage());
			}
		} else {
			selfUpdateYtDlp(ytDlpFile);
		}
		if (ytDlpFile.exists()) {
			boolean granted = ytDlpFile.setExecutable(true);
			if (!granted && !os.contains("win")) {
				System.err.println("Warning: Please help me get yt-dlp permission!");
			}
		}
		if (!fFmpegFile.exists()) {
			System.out.println("Warning: Not found " + fFmpegName + "!");
			System.out.println("Audio and video may be separated");
		} else {
			fFmpegFile.setExecutable(true);
		}
	}

	// Site extractors break constantly; keep yt-dlp fresh without blocking startup.
	private static void selfUpdateYtDlp(File ytDlpFile) {
		Thread updater = new Thread(() -> {
			try {
				System.out.println("[Updater] Checking for yt-dlp updates...");
				Process p = new ProcessBuilder(ytDlpFile.getAbsolutePath(), "-U").redirectErrorStream(true).start();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						System.out.println("[Updater] " + line);
					}
				}
				p.waitFor();
			} catch (Exception e) {
				System.err.println("[Updater] yt-dlp self-update failed: " + e.getMessage());
			}
		});
		updater.setDaemon(true);
		updater.start();
	}
}