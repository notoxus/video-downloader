package com.videodownloader.controller;

import java.io.File;

/** Locates the bundled yt-dlp / ffmpeg binaries for the current OS and architecture. */
public final class ToolPaths {

	private ToolPaths() {
	}

	public static String get(String toolType) {
		String os = System.getProperty("os.name").toLowerCase();
		String arch = System.getProperty("os.arch").toLowerCase();

		String fileName = "";
		if (toolType.equals("ytdlp")) {
			if (os.contains("win"))
				fileName = "yt-dlp.exe";
			else if (os.contains("mac"))
				fileName = "yt-dlp-macos";
			else
				fileName = "yt-dlp";
		} else if (toolType.equals("ffmpeg")) {
			if (os.contains("win"))
				fileName = "ffmpeg.exe";
			else if (os.contains("mac"))
				fileName = "ffmpeg-macos";
			else if (arch.contains("aarch64") || arch.contains("arm"))
				fileName = "ffmpeg-linux-arm64";
			else
				fileName = "ffmpeg-linux-x64";
		}

		String projectRoot = System.getProperty("user.dir");
		String jarDir = projectRoot;
		try {
			File jarPath = new File(
					ToolPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			jarDir = jarPath.getParent();
		} catch (Exception ignored) {
		}

		File toolInDevFolder = new File(projectRoot + File.separator + "tools" + File.separator + fileName);
		if (toolInDevFolder.exists()) {
			return toolInDevFolder.getAbsolutePath();
		}

		File toolInJarDir = new File(jarDir + File.separator + fileName);
		if (toolInJarDir.exists()) {
			return toolInJarDir.getAbsolutePath();
		}

		return jarDir + File.separator + fileName;
	}
}
