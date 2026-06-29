package com.videodownloader.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import com.videodownloader.controller.BrowserController;
import com.videodownloader.controller.DownloadManager;

public class AppGUI extends JFrame {
	private static final long serialVersionUID = 1L;
	private JTextField urlInput;
	private JButton btnClipboard, btnHunt, btnImportApi;
	private DefaultTableModel tableModel;
	private JTable queueTable;
	private JTextArea consoleLog;
	private JButton btnDownloadSelected;

	private final DownloadManager manager;

	private int hoveredRow = -1;
	private int hoveredCol = -1;

	public AppGUI(DownloadManager manager) {
		this.manager = manager;

		setTitle("Video Downloader - v1.0.2");
		setSize(1050, 660);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(10, 10));

		JPanel topPanel = new JPanel(new BorderLayout(10, 10));
		topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));

		JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
		JLabel urlLabel = new JLabel("URL:");
		urlLabel.setFont(urlLabel.getFont().deriveFont(Font.BOLD));
		inputPanel.add(urlLabel, BorderLayout.WEST);
		urlInput = new JTextField();
		urlInput.setToolTipText("Paste a video URL here, or leave empty to open a capture browser");
		inputPanel.add(urlInput, BorderLayout.CENTER);

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		btnImportApi = new JButton("Import API JSON");
		btnImportApi.setToolTipText("Import a JSON episode list for bulk downloads");
		btnClipboard = new JButton("From Clipboard");
		btnClipboard.setToolTipText("Parse video URLs from clipboard content");
		btnHunt = new JButton("Hunt / Download  ↵");
		btnHunt.setToolTipText("Detect stream URL via browser extension, or download directly");
		btnHunt.putClientProperty("JButton.buttonType", "roundRect");
		btnPanel.add(btnImportApi);
		btnPanel.add(btnClipboard);
		btnPanel.add(btnHunt);

		topPanel.add(inputPanel, BorderLayout.CENTER);
		topPanel.add(btnPanel, BorderLayout.EAST);

		JPanel queuePanel = new JPanel(new BorderLayout());
		queuePanel.setBorder(BorderFactory.createTitledBorder("Download Queue"));

		String[] columns = { "Ord No.", "Link video/playlist", "Format", "Status", "Progress", " " };
		tableModel = new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		queueTable = new JTable(tableModel);
		queueTable.setRowHeight(30);
		queueTable.setShowHorizontalLines(false);
		queueTable.setShowVerticalLines(false);
		queueTable.setIntercellSpacing(new java.awt.Dimension(0, 1));
		queueTable.setFillsViewportHeight(true);
		queueTable.getTableHeader().setReorderingAllowed(false);

		queueTable.getColumnModel().getColumn(0).setPreferredWidth(50);
		queueTable.getColumnModel().getColumn(0).setMaxWidth(50);
		queueTable.getColumnModel().getColumn(2).setPreferredWidth(70);
		queueTable.getColumnModel().getColumn(2).setMaxWidth(70);
		queueTable.getColumnModel().getColumn(3).setPreferredWidth(110);
		queueTable.getColumnModel().getColumn(3).setMaxWidth(130);
		ProgressRenderer progressRenderer = new ProgressRenderer();
		queueTable.getColumnModel().getColumn(4).setCellRenderer(progressRenderer);

		queueTable.getColumnModel().getColumn(5).setPreferredWidth(35);
		queueTable.getColumnModel().getColumn(5).setMaxWidth(35);
		RemoveButtonRenderer removeBtnRenderer = new RemoveButtonRenderer();
		queueTable.getColumnModel().getColumn(5).setCellRenderer(removeBtnRenderer);

		queueTable.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				int row = queueTable.rowAtPoint(e.getPoint());
				int col = queueTable.columnAtPoint(e.getPoint());

				if (row != hoveredRow || col != hoveredCol) {
					int oldRow = hoveredRow;
					int oldCol = hoveredCol;

					hoveredRow = row;
					hoveredCol = col;

					removeBtnRenderer.updateHoverState(row, col);

					if (oldRow >= 0 && oldCol == 5) {
						queueTable.repaint(queueTable.getCellRect(oldRow, oldCol, false));
					}
					if (hoveredRow >= 0 && hoveredCol == 5) {
						queueTable.repaint(queueTable.getCellRect(hoveredRow, hoveredCol, false));
					}
				}
			}
		});

		queueTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseExited(MouseEvent e) {
				int oldRow = hoveredRow;
				int oldCol = hoveredCol;

				hoveredRow = -1;
				hoveredCol = -1;
				removeBtnRenderer.updateHoverState(-1, -1);

				if (oldRow >= 0 && oldCol == 5) {
					queueTable.repaint(queueTable.getCellRect(oldRow, oldCol, false));
				}
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				int column = queueTable.columnAtPoint(e.getPoint());
				int row = queueTable.rowAtPoint(e.getPoint());

				if (row < queueTable.getRowCount() && row >= 0 && column == 5) {
					String status = tableModel.getValueAt(row, 3).toString();
					if (status.equals("Downloading...")) {
						logToConsole("=> [System] Cannot remove an active download!");
						return;
					}

					manager.removePendingTask(row);
					tableModel.removeRow(row);

					for (int i = 0; i < tableModel.getRowCount(); i++) {
						tableModel.setValueAt(i + 1, i, 0);
					}
					logToConsole("=> [System] Removed item from queue.");

					hoveredRow = -1;
					hoveredCol = -1;
					removeBtnRenderer.updateHoverState(-1, -1);
				}
			}
		});

		JScrollPane tableScroll = new JScrollPane(queueTable);
		queuePanel.add(tableScroll, BorderLayout.CENTER);

		JPanel bottomContainer = new JPanel(new BorderLayout(5, 5));
		bottomContainer.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

		JPanel consolePanel = new JPanel(new BorderLayout());
		consolePanel.setBorder(BorderFactory.createTitledBorder("Console Log"));
		consoleLog = new JTextArea(7, 50);
		consoleLog.setEditable(false);
		consoleLog.setBackground(new Color(28, 28, 28));
		consoleLog.setForeground(new Color(180, 210, 180));
		consoleLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
		consoleLog.setMargin(new java.awt.Insets(4, 6, 4, 6));
		consolePanel.add(new JScrollPane(consoleLog), BorderLayout.CENTER);

		JPanel actionPanel = new JPanel(new BorderLayout(0, 5));
		btnDownloadSelected = new JButton("Download Selected");
		btnDownloadSelected.setToolTipText("Start downloading all selected items in the queue");
		btnDownloadSelected.putClientProperty("JButton.buttonType", "roundRect");

		actionPanel.add(btnDownloadSelected, BorderLayout.NORTH);

		bottomContainer.add(consolePanel, BorderLayout.CENTER);
		bottomContainer.add(actionPanel, BorderLayout.SOUTH);

		add(topPanel, BorderLayout.NORTH);
		add(queuePanel, BorderLayout.CENTER);
		add(bottomContainer, BorderLayout.SOUTH);

		btnHunt.addActionListener(e -> startHunting());

		urlInput.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					startHunting();
				}
			}
		});

		btnDownloadSelected.addActionListener(e -> {
			int[] selectedRows = queueTable.getSelectedRows();
			if (selectedRows.length == 0) {
				JOptionPane.showMessageDialog(this, "Please choose at least one URL to download!", "Not chose file yet",
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			logToConsole("=> Starting " + selectedRows.length + " selected items...");

			for (int row : selectedRows) {
				manager.enqueuePendingTask(row);
				updateQueueItemStatus(row, "In Queue", "0%");
			}
			queueTable.clearSelection();
		});

		btnImportApi.addActionListener(e -> {
			JTextArea jsonArea = new JTextArea(15, 60);
			jsonArea.setLineWrap(true);
			JScrollPane scrollPane = new JScrollPane(jsonArea);

			int result = JOptionPane.showConfirmDialog(this, scrollPane, "Paste your API JSON here:",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

			if (result == JOptionPane.OK_OPTION) {
				String jsonContent = jsonArea.getText().trim();
				if (!jsonContent.isEmpty()) {
					logToConsole("=> [System] Parsing API JSON...");
					manager.processApiJson(jsonContent);
				}
			}
		});

		btnClipboard.addActionListener(e -> {
			try {
				Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
					String data = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
					if (data != null && !data.trim().isEmpty()) {
						Matcher m = Pattern.compile("(?i)https?://[^\\s]+").matcher(data);
						boolean found = false;
						while (m.find()) {
							found = true;
							String url = m.group();
							logToConsole("=> [Clipboard] Manual catch: " + url);
							manager.processLink(url);
						}

						if (!found) {
							String preview = data.length() > 30 ? data.substring(0, 30) + "..." : data;
							logToConsole("=> [Clipboard] No valid link found. Copied text was: '" + preview + "'");
						}
					} else {
						logToConsole("=> [Clipboard] Clipboard is empty.");
					}
				}
			} catch (Exception ex) {
				logToConsole("=> [Error] Cannot access clipboard: " + ex.getMessage());
			}
		});
	}

	private void startHunting() {
		String movieUrl = urlInput.getText().trim();
		String lowerUrl = movieUrl.toLowerCase();

		if (movieUrl.isEmpty()) {
			logToConsole("=> [Hunter] Launching Native Chrome for manual browsing...");
			BrowserController.openCaptureBrowser("");
			return;
		}

		if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") || lowerUrl.contains("tiktok.com")
				|| lowerUrl.contains("facebook.com") || lowerUrl.contains("instagram.com")) {
			logToConsole("=> [System] Detected native platform. Direct download...");
			manager.processLink(movieUrl);
		} else {
			logToConsole("=> [Hunter] Launching Native Chrome Engine...");
			BrowserController.openCaptureBrowser(movieUrl);
		}

		urlInput.setText("");
	}

	public void logToConsole(String message) {
		SwingUtilities.invokeLater(() -> {
			consoleLog.append(message + "\n");
			consoleLog.setCaretPosition(consoleLog.getDocument().getLength());
		});
	}

	public int addQueueItem(String url, String format, String status) {
		int stt = tableModel.getRowCount() + 1;
		tableModel.addRow(new Object[] { stt, url, format, status, "0%", "" });
		return tableModel.getRowCount() - 1;
	}

	public void updateQueueItemStatus(int rowIndex, String status, String progress) {
		SwingUtilities.invokeLater(() -> {
			if (rowIndex >= 0 && rowIndex < tableModel.getRowCount()) {
				tableModel.setValueAt(status, rowIndex, 3);
				tableModel.setValueAt(progress, rowIndex, 4);
			}
		});
	}

	public int getRowCount() {
		return tableModel.getRowCount();
	}

}