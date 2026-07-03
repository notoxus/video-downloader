package com.videodownloader.model;

import java.util.List;

public interface DownloadStrategy {
	VideoInfo fetchMetadata(String url) throws Exception;

	public void startDownload(String url, String savePath, String format, String trimSection, boolean preciseCut,
			Observer observer);

	List<String> extractPlaylistLinks(String playlistUrl) throws Exception;
}