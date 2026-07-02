package com.videodownloader.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.videodownloader.controller.MediaProbe;

/**
 * Asks the user for a download format and an optional trim range.
 * When trimming is enabled and the media duration is known, the user selects
 * the start/end points with a dual-thumb slider over a thumbnail filmstrip
 * instead of typing timestamps.
 */
public class DownloadOptionsDialog {

	public static class Options {
		public final String format; // mp4 | mp3 | mkv
		public final String trimSection; // yt-dlp --download-sections spec, or null
		public final boolean preciseCut; // true = frame-accurate (re-encode), false = fast keyframe copy

		private Options(String format, String trimSection, boolean preciseCut) {
			this.format = format;
			this.trimSection = trimSection;
			this.preciseCut = preciseCut;
		}
	}

	/**
	 * Shows the dialog. Safe to call from any thread. Returns null if cancelled.
	 *
	 * @param message       heading text
	 * @param url           media URL (used to probe duration / thumbnails); may be null
	 * @param durationHint  known duration in seconds, or 0 to probe in the background
	 * @param refererUrl    referer for thumbnail extraction (original page URL), may be null
	 */
	public static Options show(String message, String url, int durationHint, String refererUrl) {
		if (SwingUtilities.isEventDispatchThread()) {
			return showImpl(message, url, durationHint, refererUrl);
		}
		final Options[] holder = new Options[1];
		try {
			SwingUtilities.invokeAndWait(() -> holder[0] = showImpl(message, url, durationHint, refererUrl));
		} catch (InterruptedException | InvocationTargetException e) {
			Thread.currentThread().interrupt();
		}
		return holder[0];
	}

	private static Options showImpl(String message, String url, int durationHint, String refererUrl) {
		JRadioButton rbMp4 = new JRadioButton("MP4 — Video (recommended)", true);
		JRadioButton rbMp3 = new JRadioButton("MP3 — Audio only");
		JRadioButton rbMkv = new JRadioButton("MKV — Original quality");
		ButtonGroup formatGroup = new ButtonGroup();
		formatGroup.add(rbMp4);
		formatGroup.add(rbMp3);
		formatGroup.add(rbMkv);

		JCheckBox cbTrim = new JCheckBox("Cut a section before downloading");
		JCheckBox cbPrecise = new JCheckBox("Frame-accurate cut (slower — re-encodes)");
		cbPrecise.setEnabled(false);

		FilmstripPanel filmstrip = new FilmstripPanel();
		RangeSlider slider = new RangeSlider();
		slider.setEnabled(false);
		JLabel rangeLabel = new JLabel(" ");
		JLabel statusLabel = new JLabel("Enable trimming to load the preview.");
		statusLabel.setForeground(new Color(150, 150, 150));

		Runnable updateRangeLabel = () -> {
			int lo = slider.getLow();
			int hi = slider.getHigh();
			rangeLabel.setText("From  " + fmt(lo) + "   →   To  " + fmt(hi) + "      (clip length " + fmt(hi - lo)
					+ ")");
		};
		slider.addChangeListener(() -> {
			updateRangeLabel.run();
			filmstrip.setSelection((double) slider.getLow() / Math.max(1, slider.getMax()),
					(double) slider.getHigh() / Math.max(1, slider.getMax()));
		});

		JPanel trimBox = new JPanel();
		trimBox.setLayout(new BoxLayout(trimBox, BoxLayout.Y_AXIS));
		trimBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 18, 0, 0));
		filmstrip.setAlignmentX(Component.LEFT_ALIGNMENT);
		slider.setAlignmentX(Component.LEFT_ALIGNMENT);
		rangeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		cbPrecise.setAlignmentX(Component.LEFT_ALIGNMENT);
		trimBox.add(statusLabel);
		trimBox.add(Box.createVerticalStrut(4));
		trimBox.add(filmstrip);
		trimBox.add(slider);
		trimBox.add(rangeLabel);
		trimBox.add(Box.createVerticalStrut(4));
		trimBox.add(cbPrecise);
		setTrimEnabled(trimBox, false);

		cbTrim.addActionListener(e -> {
			boolean on = cbTrim.isSelected();
			setTrimEnabled(trimBox, on);
			cbPrecise.setEnabled(on);
			if (on) {
				loadPreview(url, durationHint, refererUrl, slider, filmstrip, statusLabel, updateRangeLabel);
			}
		});

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JLabel msg = new JLabel("<html>" + escapeHtml(message).replace("\n", "<br>") + "</html>");
		for (JComponent c : new JComponent[] { msg, rbMp4, rbMp3, rbMkv, cbTrim, trimBox }) {
			c.setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		panel.add(msg);
		panel.add(Box.createVerticalStrut(12));
		panel.add(rbMp4);
		panel.add(rbMp3);
		panel.add(rbMkv);
		panel.add(Box.createVerticalStrut(10));
		panel.add(cbTrim);
		panel.add(trimBox);

		JScrollPane scrollPane = new JScrollPane(panel);
		scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		int result = JOptionPane.showConfirmDialog(null, scrollPane, "Download Options", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION) {
			return null;
		}

		String format = rbMp3.isSelected() ? "mp3" : (rbMkv.isSelected() ? "mkv" : "mp4");

		if (!cbTrim.isSelected() || !slider.isEnabled()) {
			return new Options(format, null, false);
		}

		int lo = slider.getLow();
		int hi = slider.getHigh();
		// Full range selected => treat as no trim.
		if (lo <= 0 && hi >= slider.getMax()) {
			return new Options(format, null, false);
		}
		String from = String.valueOf(lo);
		String to = (hi >= slider.getMax()) ? "inf" : String.valueOf(hi);
		return new Options(format, "*" + from + "-" + to, cbPrecise.isSelected());
	}

	private static void loadPreview(String url, int durationHint, String refererUrl, RangeSlider slider,
			FilmstripPanel filmstrip, JLabel statusLabel, Runnable updateRangeLabel) {
		statusLabel.setText("Loading video info...");
		new Thread(() -> {
			int duration = durationHint > 0 ? durationHint : MediaProbe.probeDurationSeconds(url);
			if (duration <= 0) {
				SwingUtilities.invokeLater(() -> statusLabel.setText(
						"Couldn't read the video length — trimming is unavailable for this source."));
				return;
			}
			SwingUtilities.invokeLater(() -> {
				slider.setMax(duration);
				slider.setRange(0, duration);
				slider.setEnabled(true);
				updateRangeLabel.run();
				statusLabel.setText("Drag the two handles to pick the start and end. Loading preview frames...");
			});

			// Filmstrip is best-effort and can be slow; never blocks the slider.
			String direct = MediaProbe.resolveDirectUrl(url);
			List<BufferedImage> frames = MediaProbe.extractThumbnails(direct, duration, 8, refererUrl);
			SwingUtilities.invokeLater(() -> {
				if (frames.isEmpty()) {
					filmstrip.setUnavailable();
					statusLabel.setText("Drag the two handles to pick the start and end.");
				} else {
					filmstrip.setFrames(frames);
					statusLabel.setText("Drag the two handles to pick the start and end.");
				}
			});
		}, "trim-preview-loader").start();
	}

	private static void setTrimEnabled(JPanel trimBox, boolean enabled) {
		trimBox.setVisible(enabled);
	}

	private static String fmt(int totalSeconds) {
		if (totalSeconds < 0) {
			totalSeconds = 0;
		}
		int h = totalSeconds / 3600;
		int m = (totalSeconds % 3600) / 60;
		int s = totalSeconds % 60;
		if (h > 0) {
			return String.format("%d:%02d:%02d", h, m, s);
		}
		return String.format("%d:%02d", m, s);
	}

	private static String escapeHtml(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Horizontal strip of preview thumbnails with a highlighted selected range. */
	private static class FilmstripPanel extends JComponent {
		private static final long serialVersionUID = 1L;
		private static final int SIDE_PAD = 10; // must match RangeSlider's internal padding
		private List<BufferedImage> frames;
		private String placeholder = "";
		private double selLow = 0.0;
		private double selHigh = 1.0;

		FilmstripPanel() {
			setPreferredSize(new Dimension(400, 70));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		}

		void setFrames(List<BufferedImage> frames) {
			this.frames = frames;
			this.placeholder = "";
			repaint();
		}

		void setUnavailable() {
			this.frames = null;
			this.placeholder = "(preview not available for this source — the slider still works)";
			repaint();
		}

		void setSelection(double low, double high) {
			this.selLow = low;
			this.selHigh = high;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int x0 = SIDE_PAD;
			int w = getWidth() - 2 * SIDE_PAD;
			int h = getHeight() - 8;
			int y = 2;

			g2.setColor(new Color(45, 45, 48));
			g2.fillRect(x0, y, w, h);

			if (frames != null && !frames.isEmpty()) {
				int n = frames.size();
				double cellW = (double) w / n;
				for (int i = 0; i < n; i++) {
					BufferedImage img = frames.get(i);
					int cx = x0 + (int) (i * cellW);
					int cw = (int) Math.ceil(cellW);
					if (img != null) {
						g2.drawImage(img, cx, y, cw, h, null);
					}
				}
			} else {
				g2.setColor(new Color(150, 150, 150));
				String text = placeholder.isEmpty() ? "" : placeholder;
				g2.drawString(text, x0 + 8, y + h / 2);
			}

			// Dim the parts outside the selected range.
			int selX0 = x0 + (int) (selLow * w);
			int selX1 = x0 + (int) (selHigh * w);
			g2.setColor(new Color(0, 0, 0, 130));
			if (selX0 > x0) {
				g2.fillRect(x0, y, selX0 - x0, h);
			}
			if (selX1 < x0 + w) {
				g2.fillRect(selX1, y, (x0 + w) - selX1, h);
			}
			// Selection borders
			g2.setColor(new Color(66, 133, 244));
			g2.drawLine(selX0, y, selX0, y + h);
			g2.drawLine(selX1, y, selX1, y + h);

			g2.dispose();
		}
	}
}
