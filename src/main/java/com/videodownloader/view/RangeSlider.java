package com.videodownloader.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

/**
 * A horizontal slider with two draggable thumbs defining a [low, high] range
 * over an integer domain [0, max]. Fires listeners whenever either thumb moves.
 */
public class RangeSlider extends JComponent {
	private static final long serialVersionUID = 1L;

	private int max = 100;
	private int low = 0;
	private int high = 100;

	private static final int THUMB_R = 8;
	private static final int PAD = THUMB_R + 2;
	private int dragging = 0; // 0 = none, 1 = low, 2 = high

	private final List<Runnable> listeners = new ArrayList<>();

	private final Color trackColor = new Color(80, 80, 85);
	private final Color rangeColor = new Color(66, 133, 244);
	private final Color thumbColor = new Color(230, 230, 235);
	private final Color thumbBorder = new Color(66, 133, 244);

	public RangeSlider() {
		setPreferredSize(new Dimension(400, 36));
		setMinimumSize(new Dimension(120, 36));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		MouseAdapter ma = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!isEnabled()) {
					return;
				}
				int lowX = valueToX(low);
				int highX = valueToX(high);
				// Pick the nearer thumb to the click.
				if (Math.abs(e.getX() - lowX) <= Math.abs(e.getX() - highX)) {
					dragging = 1;
				} else {
					dragging = 2;
				}
				updateFromMouse(e.getX());
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				dragging = 0;
			}
		};
		addMouseListener(ma);
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (isEnabled()) {
					updateFromMouse(e.getX());
				}
			}
		});
	}

	public int getMax() {
		return max;
	}

	public void setMax(int max) {
		this.max = Math.max(1, max);
		this.low = Math.max(0, Math.min(low, this.max));
		this.high = Math.max(low, Math.min(high, this.max));
		repaint();
	}

	public void setRange(int low, int high) {
		this.low = Math.max(0, Math.min(low, max));
		this.high = Math.max(this.low, Math.min(high, max));
		repaint();
		fire();
	}

	public int getLow() {
		return low;
	}

	public int getHigh() {
		return high;
	}

	public void addChangeListener(Runnable r) {
		listeners.add(r);
	}

	private void fire() {
		for (Runnable r : listeners) {
			r.run();
		}
	}

	private int trackWidth() {
		return getWidth() - 2 * PAD;
	}

	private int valueToX(int value) {
		return PAD + (int) ((double) value / max * trackWidth());
	}

	private int xToValue(int x) {
		double frac = (double) (x - PAD) / trackWidth();
		int v = (int) Math.round(frac * max);
		return Math.max(0, Math.min(max, v));
	}

	private void updateFromMouse(int x) {
		int v = xToValue(x);
		if (dragging == 1) {
			low = Math.min(v, high);
		} else if (dragging == 2) {
			high = Math.max(v, low);
		} else {
			return;
		}
		repaint();
		fire();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int cy = getHeight() / 2;
		int lowX = valueToX(low);
		int highX = valueToX(high);

		boolean on = isEnabled();

		// Base track
		g2.setColor(trackColor);
		g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.drawLine(PAD, cy, getWidth() - PAD, cy);

		// Selected range
		g2.setColor(on ? rangeColor : trackColor.brighter());
		g2.drawLine(lowX, cy, highX, cy);

		// Thumbs
		drawThumb(g2, lowX, cy);
		drawThumb(g2, highX, cy);

		g2.dispose();
	}

	private void drawThumb(Graphics2D g2, int x, int cy) {
		g2.setColor(thumbColor);
		g2.fillOval(x - THUMB_R, cy - THUMB_R, THUMB_R * 2, THUMB_R * 2);
		g2.setColor(thumbBorder);
		g2.setStroke(new BasicStroke(2));
		g2.drawOval(x - THUMB_R, cy - THUMB_R, THUMB_R * 2, THUMB_R * 2);
	}
}
