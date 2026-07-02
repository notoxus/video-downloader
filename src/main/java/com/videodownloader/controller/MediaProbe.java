package com.videodownloader.controller;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/**
 * Best-effort media inspection using the bundled yt-dlp / ffmpeg binaries.
 * Used by the trim dialog to bound the range slider and build a filmstrip.
 * Every method degrades gracefully: failures return 0 / an empty list rather than throwing.
 */
public class MediaProbe {

	/** Returns the media duration in seconds, or 0 if it can't be determined. */
	public static int probeDurationSeconds(String url) {
		try {
			ProcessBuilder pb = new ProcessBuilder(ToolPaths.get("ytdlp"), "--extractor-args",
					"generic:impersonate", "--no-warnings", "--print", "%(duration)s", url);
			pb.redirectErrorStream(false);
			Process p = pb.start();
			String out;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				out = r.readLine();
			}
			if (!p.waitFor(45, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return 0;
			}
			if (out != null) {
				out = out.trim();
				if (out.matches("\\d+(\\.\\d+)?")) {
					return (int) Math.round(Double.parseDouble(out));
				}
			}
		} catch (Exception ignored) {
		}
		return 0;
	}

	/**
	 * Resolves a single direct media URL that ffmpeg can read (muxed if possible).
	 * Returns null on failure.
	 */
	public static String resolveDirectUrl(String url) {
		try {
			ProcessBuilder pb = new ProcessBuilder(ToolPaths.get("ytdlp"), "--extractor-args",
					"generic:impersonate", "--no-warnings", "-f", "best[height<=480]/best", "-g", url);
			Process p = pb.start();
			String direct;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				direct = r.readLine();
			}
			if (!p.waitFor(45, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			return (direct != null && direct.startsWith("http")) ? direct.trim() : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Extracts {@code count} evenly-spaced thumbnails across the given duration.
	 * Runs ffmpeg once per frame with a fast pre-input seek. Returns whatever it manages
	 * to grab (possibly empty) so the caller can show a partial or no filmstrip.
	 */
	public static List<BufferedImage> extractThumbnails(String directUrl, int durationSeconds, int count,
			String referer) {
		List<BufferedImage> frames = new ArrayList<>();
		if (directUrl == null || durationSeconds <= 0 || count <= 0) {
			return frames;
		}

		String ffmpeg = ToolPaths.get("ffmpeg");
		for (int i = 0; i < count; i++) {
			// Sample slightly inside the range so we skip black intros / end cards.
			double fraction = (i + 0.5) / count;
			int ts = (int) (durationSeconds * fraction);
			BufferedImage img = grabFrame(ffmpeg, directUrl, ts, referer);
			if (img != null) {
				frames.add(img);
			}
		}
		return frames;
	}

	private static BufferedImage grabFrame(String ffmpeg, String url, int timestampSec, String referer) {
		Process p = null;
		try {
			List<String> cmd = new ArrayList<>();
			cmd.add(ffmpeg);
			cmd.add("-y");
			if (referer != null && !referer.isBlank()) {
				cmd.add("-headers");
				cmd.add("Referer: " + referer + "\r\n");
			}
			cmd.add("-ss");
			cmd.add(String.valueOf(timestampSec)); // fast seek before -i
			cmd.add("-i");
			cmd.add(url);
			cmd.add("-frames:v");
			cmd.add("1");
			cmd.add("-vf");
			cmd.add("scale=160:-1");
			cmd.add("-f");
			cmd.add("image2pipe");
			cmd.add("-vcodec");
			cmd.add("mjpeg");
			cmd.add("pipe:1");

			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.redirectErrorStream(false);
			p = pb.start();

			// Drain stderr in the background so ffmpeg never blocks on a full pipe.
			Process finalP = p;
			Thread errDrain = new Thread(() -> {
				try (InputStream es = finalP.getErrorStream()) {
					byte[] buf = new byte[4096];
					while (es.read(buf) != -1) {
					}
				} catch (Exception ignored) {
				}
			});
			errDrain.setDaemon(true);
			errDrain.start();

			BufferedImage img;
			try (InputStream is = p.getInputStream()) {
				img = ImageIO.read(is);
			}
			if (!p.waitFor(20, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return null;
			}
			return img;
		} catch (Exception e) {
			if (p != null) {
				p.destroyForcibly();
			}
			return null;
		}
	}
}
