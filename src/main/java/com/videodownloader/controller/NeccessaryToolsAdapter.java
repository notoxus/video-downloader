package com.videodownloader.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.videodownloader.model.DownloadStrategy;
import com.videodownloader.model.Observer;
import com.videodownloader.model.VideoInfo;

public class NeccessaryToolsAdapter implements DownloadStrategy {
	private final Gson gson = new Gson();

	private String getToolPath(String toolType) {
		return ToolPaths.get(toolType);
	}

	@Override
	public VideoInfo fetchMetadata(String url) throws Exception {
		String ytDlpCommand = getToolPath("ytdlp");

		ProcessBuilder pb = new ProcessBuilder(ytDlpCommand, "--extractor-args", "generic:impersonate", "--dump-json",
				"--no-warnings", url);
		Process process = pb.start();

		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String firstLine = reader.readLine();

		BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		StringBuilder errorMsg = new StringBuilder();
		String line;
		while ((line = errorReader.readLine()) != null) {
			errorMsg.append(line).append("\n");
		}
		if (firstLine != null && !firstLine.trim().isEmpty()) {
			return gson.fromJson(firstLine, VideoInfo.class);
		} else {
			throw new RuntimeException("Analytics link error: \n" + errorMsg.toString());
		}
	}

	@Override
	public void startDownload(String url, String savePath, String format, String trimSection, boolean preciseCut,
			Observer o) {
		try {
			System.out.println("Processing format: " + format.toUpperCase() + "...");

			String outputTemplate = savePath + File.separator + "%(title)s.%(ext)s";

			List<String> commandList = new ArrayList<>();
			commandList.add(getToolPath("ytdlp"));

			commandList.add("--extractor-args");
			commandList.add("generic:impersonate");
			commandList.add("--add-header");
			commandList.add("Referer: " + url);

			commandList.add("--ffmpeg-location");
			commandList.add(getToolPath("ffmpeg"));

			commandList.add("-N");
			commandList.add("16");
			commandList.add("--fragment-retries");
			commandList.add("10");
			commandList.add("--retry-sleep");
			commandList.add("3");

			if (trimSection != null && !trimSection.isBlank()) {
				System.out.println("Trimming section: " + trimSection + (preciseCut ? " (precise)" : " (fast)"));
				commandList.add("--download-sections");
				commandList.add(trimSection);
				// By default cut on keyframes with a stream copy (fast). Only re-encode
				// when the user explicitly asks for a frame-accurate cut.
				if (preciseCut) {
					commandList.add("--force-keyframes-at-cuts");
				}
			}

			if (format.equalsIgnoreCase("mp4")) {
				commandList.add("-f");
				commandList.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best");
				commandList.add("--merge-output-format");
				commandList.add("mp4/mkv");
			} else if (format.equalsIgnoreCase("mp3")) {
				commandList.add("-f");
				commandList.add("bestaudio/best");
				commandList.add("-x");
				commandList.add("--audio-format");
				commandList.add("mp3");

			} else {
				commandList.add("-f");
				commandList.add("bestvideo+bestaudio/best");
				commandList.add("--remux-video");
				commandList.add("mkv");
			}

			commandList.add("-o");
			commandList.add(outputTemplate);
			commandList.add(url);

			ProcessBuilder pb = new ProcessBuilder(commandList);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;

			while ((line = reader.readLine()) != null) {
				if (line.contains("[download]") && line.contains("%")) {
					try {
						int percentIndex = line.indexOf("%");
						int spaceBeforePercent = line.lastIndexOf(" ", percentIndex);
						String percentStr = line.substring(spaceBeforePercent + 1, percentIndex).trim();
						double percent = Double.parseDouble(percentStr);

						String speed = "N/A";
						if (line.contains("at ")) {
							int atIndex = line.indexOf("at ");
							int posAfterAt = atIndex + 3;
							int spaceAfterSpeed = line.indexOf(" ", posAfterAt);
							if (spaceAfterSpeed != -1) {
								speed = line.substring(posAfterAt, spaceAfterSpeed).trim();
							}
						}
						o.onProgressUpdate(url, percent, speed);
					} catch (Exception ignored) {
					}
				} else if (line.toLowerCase().contains("error") || line.toLowerCase().contains("warning")) {
					System.err.println("\n[yt-dlp log] " + line);
				} else if (line.contains("[Merger]")) {
					System.out.print("\r[System] Processing merge... Waiting for minutes.\n");
				}
			}

			int exitCode = process.waitFor();

			if (exitCode == 0) {
				System.out.println("\nDownload completed! Save at: " + savePath);
				o.onComplete(url, savePath);
			} else {
				throw new RuntimeException("yt-dlp was crashed (Exit code: " + exitCode + ")");
			}
		} catch (Exception e) {
			System.err.println("\nError while downloading: " + e.getMessage());
			o.onError(url, e.getMessage());
		}
	}

	@Override
	public List<String> extractPlaylistLinks(String playlistUrl) throws Exception {
		List<String> links = new ArrayList<>();
		ProcessBuilder pb = new ProcessBuilder(getToolPath("ytdlp"), "--flat-playlist", "--print", "webpage_url",
				playlistUrl);
		Process process = pb.start();
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("http")) {
				links.add(line);
			}
		}
		return links;
	}
}