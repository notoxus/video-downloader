package com.videodownloader.model;

import java.util.List;

public interface DownloadStrategy {
	VideoInfo fetchMetadata(String url) throws Exception;

	/**
	 * @param trimSection yt-dlp --download-sections spec (e.g. "*00:30-01:45"), or null for the full video
	 * @param preciseCut  true = frame-accurate cut (re-encode); false = fast keyframe-aligned copy
	 */
	public void startDownload(String url, String savePath, String format, String trimSection, boolean preciseCut,
			Observer observer);

	List<String> extractPlaylistLinks(String playlistUrl) throws Exception;
}