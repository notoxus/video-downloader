package com.videodownloader.controller;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.swing.JOptionPane;

import com.videodownloader.model.BrowserEngine;

public class BrowserController {

	private static final String BASE_CONFIG_PATH = System.getProperty("user.home") + File.separator
			+ ".VideoDownloaderApp";
	private static final String CHROME_PROFILE_PATH = BASE_CONFIG_PATH + File.separator + "ChromeProfile";
	private static final String FIREFOX_PROFILE_PATH = BASE_CONFIG_PATH + File.separator + "FirefoxProfile";
	private static final File SETTINGS_FILE = new File(BASE_CONFIG_PATH, "settings.properties");

	private static BrowserEngine currentEngine = loadSavedEngine();

	public static BrowserEngine getEngine() {
		return currentEngine;
	}

	public static void setEngine(BrowserEngine engine) {
		currentEngine = engine != null ? engine : BrowserEngine.AUTO;
		saveEngine(currentEngine);
	}

	public static BrowserEngine loadSavedEngine() {
		try {
			if (SETTINGS_FILE.exists()) {
				Properties props = new Properties();
				try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
					props.load(in);
					String val = props.getProperty("browser_engine");
					if (val != null) {
						return BrowserEngine.fromString(val);
					}
				}
			}
		} catch (Exception ignored) {
		}
		return BrowserEngine.AUTO;
	}

	public static void saveEngine(BrowserEngine engine) {
		try {
			if (!SETTINGS_FILE.getParentFile().exists()) {
				SETTINGS_FILE.getParentFile().mkdirs();
			}
			Properties props = new Properties();
			if (SETTINGS_FILE.exists()) {
				try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
					props.load(in);
				}
			}
			props.setProperty("browser_engine", engine.name());
			try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
				props.store(out, "VideoDownloader App Settings");
			}
		} catch (Exception e) {
			System.err.println("[BrowserController] Could not save settings: " + e.getMessage());
		}
	}

	public static BrowserEngine resolveActiveEngine() {
		if (currentEngine != BrowserEngine.AUTO) {
			return currentEngine;
		}
		if (isChromiumAvailable()) {
			return BrowserEngine.CHROMIUM;
		}
		if (isFirefoxAvailable()) {
			return BrowserEngine.MOZILLA;
		}
		return BrowserEngine.CHROMIUM;
	}

	public static boolean isChromiumAvailable() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			return findWindowsExecutable("chrome.exe", "msedge.exe", "brave.exe") != null;
		} else if (os.contains("mac")) {
			return hasMacApp("Google Chrome", "Chromium", "Brave Browser", "Microsoft Edge");
		} else {
			return hasLinuxBinary("google-chrome", "google-chrome-stable", "chromium", "chromium-browser",
					"brave-browser", "microsoft-edge", "microsoft-edge-stable");
		}
	}

	public static boolean isFirefoxAvailable() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			return findWindowsExecutable("firefox.exe") != null;
		} else if (os.contains("mac")) {
			return hasMacApp("Firefox", "Firefox Developer Edition", "Firefox Nightly", "LibreWolf");
		} else {
			return hasLinuxBinary("firefox", "firefox-esr", "librewolf", "waterfox");
		}
	}

	private static boolean hasLinuxBinary(String... binaries) {
		for (String bin : binaries) {
			File f = new File("/usr/bin", bin);
			if (f.exists() && f.canExecute()) {
				return true;
			}
			File fLocal = new File("/usr/local/bin", bin);
			if (fLocal.exists() && fLocal.canExecute()) {
				return true;
			}
			File fSnap = new File("/snap/bin", bin);
			if (fSnap.exists() && fSnap.canExecute()) {
				return true;
			}
		}
		// Fallback test via 'which'
		for (String bin : binaries) {
			try {
				Process p = new ProcessBuilder("which", bin).start();
				if (p.waitFor() == 0) {
					return true;
				}
			} catch (Exception ignored) {
			}
		}
		return false;
	}

	private static String findLinuxBinary(String... binaries) {
		for (String bin : binaries) {
			File f = new File("/usr/bin", bin);
			if (f.exists() && f.canExecute()) {
				return f.getAbsolutePath();
			}
			File fLocal = new File("/usr/local/bin", bin);
			if (fLocal.exists() && fLocal.canExecute()) {
				return fLocal.getAbsolutePath();
			}
			File fSnap = new File("/snap/bin", bin);
			if (fSnap.exists() && fSnap.canExecute()) {
				return fSnap.getAbsolutePath();
			}
		}
		for (String bin : binaries) {
			try {
				Process p = new ProcessBuilder("which", bin).start();
				if (p.waitFor() == 0) {
					return bin;
				}
			} catch (Exception ignored) {
			}
		}
		return binaries.length > 0 ? binaries[0] : "google-chrome";
	}

	private static boolean hasMacApp(String... appNames) {
		for (String name : appNames) {
			if (new File("/Applications/" + name + ".app").exists()
					|| new File(System.getProperty("user.home") + "/Applications/" + name + ".app").exists()) {
				return true;
			}
		}
		return false;
	}

	private static String findMacApp(String fallback, String... appNames) {
		for (String name : appNames) {
			if (new File("/Applications/" + name + ".app").exists()
					|| new File(System.getProperty("user.home") + "/Applications/" + name + ".app").exists()) {
				return name;
			}
		}
		return fallback;
	}

	private static String findWindowsExecutable(String... exeNames) {
		String[] prefixes = {
				System.getenv("ProgramFiles"),
				System.getenv("ProgramFiles(x86)"),
				System.getenv("LocalAppData")
		};
		String[] subDirs = {
				"Google\\Chrome\\Application\\chrome.exe",
				"Microsoft\\Edge\\Application\\msedge.exe",
				"BraveSoftware\\Brave-Browser\\Application\\brave.exe",
				"Mozilla Firefox\\firefox.exe"
		};

		for (String exe : exeNames) {
			for (String prefix : prefixes) {
				if (prefix == null) {
					continue;
				}
				for (String sub : subDirs) {
					if (sub.endsWith(exe)) {
						File f = new File(prefix, sub);
						if (f.exists()) {
							return f.getAbsolutePath();
						}
					}
				}
			}
		}
		return null;
	}

	private static ProcessBuilder getChromiumProcess(String... extraArgs) {
		String os = System.getProperty("os.name").toLowerCase();
		List<String> command = new ArrayList<>();

		if (os.contains("win")) {
			String winExe = findWindowsExecutable("chrome.exe", "msedge.exe", "brave.exe");
			if (winExe != null) {
				command.add(winExe);
			} else {
				command.add("cmd");
				command.add("/c");
				command.add("start");
				command.add("\"\"");
				command.add("chrome");
			}
		} else if (os.contains("mac")) {
			String app = findMacApp("Google Chrome", "Google Chrome", "Chromium", "Brave Browser", "Microsoft Edge");
			command.add("open");
			command.add("-a");
			command.add(app);
			command.add("--args");
		} else {
			String bin = findLinuxBinary("google-chrome", "google-chrome-stable", "chromium", "chromium-browser",
					"brave-browser", "microsoft-edge", "microsoft-edge-stable");
			command.add(bin);
		}

		command.addAll(Arrays.asList(extraArgs));
		return new ProcessBuilder(command);
	}

	private static ProcessBuilder getFirefoxProcess(String... extraArgs) {
		String os = System.getProperty("os.name").toLowerCase();
		List<String> command = new ArrayList<>();

		if (os.contains("win")) {
			String winExe = findWindowsExecutable("firefox.exe");
			if (winExe != null) {
				command.add(winExe);
			} else {
				command.add("cmd");
				command.add("/c");
				command.add("start");
				command.add("\"\"");
				command.add("firefox");
			}
		} else if (os.contains("mac")) {
			String app = findMacApp("Firefox", "Firefox", "Firefox Developer Edition", "Firefox Nightly", "LibreWolf");
			command.add("open");
			command.add("-a");
			command.add(app);
			command.add("--args");
		} else {
			String bin = findLinuxBinary("firefox", "firefox-esr", "librewolf", "waterfox");
			command.add(bin);
		}

		command.addAll(Arrays.asList(extraArgs));
		return new ProcessBuilder(command);
	}

	private static void prepareFirefoxProfile(File profileDir) {
		try {
			if (!profileDir.exists()) {
				profileDir.mkdirs();
			}
			File userJs = new File(profileDir, "user.js");
			String config = """
					user_pref("browser.aboutConfig.showWarning", false);
					user_pref("browser.shell.checkDefaultBrowser", false);
					user_pref("browser.startup.homepage_override.mstone", "ignore");
					user_pref("datareporting.policy.firstRunURL", "");
					user_pref("trailhead.firstrun.branches", "nofirstrun-empty");
					user_pref("devtools.chrome.enabled", true);
					user_pref("devtools.debugger.remote-enabled", true);
					user_pref("extensions.autoDisableScopes", 0);
					user_pref("extensions.enabledScopes", 15);
					user_pref("xpinstall.signatures.required", false);
					user_pref("extensions.experiments.enabled", true);
					""";
			try (FileWriter fw = new FileWriter(userJs)) {
				fw.write(config);
			}
		} catch (IOException e) {
			System.err.println("[FirefoxProfile] Error writing user.js: " + e.getMessage());
		}
	}

	public static void autoSetupExtensionChromium() {
		String extPath = ExtensionManager.getExtensionPath(BrowserEngine.CHROMIUM);
		try {
			System.out.println("\n[System] Preparing for first Chromium setup...");
			StringSelection stringSelection = new StringSelection(extPath);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
			String preMessage = "This is the first time you use catching link address automatically with Chromium!\n\n"
					+ "When the browser pop up. You are compulsory to do:\n"
					+ "👉 Look at the TOP RIGHT CORNER on your browser.\n"
					+ "👉 Switching the Developer mode toggle on.\n\n" + "Press OK to start open the browser.";
			JOptionPane.showMessageDialog(null, preMessage, "Step 1: Setting up Chromium Hunter",
					JOptionPane.WARNING_MESSAGE);

			ProcessBuilder pb = getChromiumProcess("--user-data-dir=" + CHROME_PROFILE_PATH,
					"--load-extension=" + extPath, "chrome://extensions/");
			pb.start();

			String postMessage = "After you have successfully enabled 'Developer mode',\n"
					+ "Please check if the 'Video Hunter' extension has appeared.\n\n"
					+ "If you see it, click OK here to start downloading the video/playlist!";
			JOptionPane.showMessageDialog(null, postMessage, "Step 2: Verification", JOptionPane.INFORMATION_MESSAGE);

			File profile = new File(CHROME_PROFILE_PATH);
			profile.mkdirs();
			new File(profile, "setup_done.txt").createNewFile();

			System.out.println("Chromium setup completed! Ready status.");

		} catch (Exception e) {
			System.err.println("Error Chromium setup: " + e.getMessage());
		}
	}

	public static void autoSetupExtensionFirefox() {
		String extPath = ExtensionManager.getExtensionPath(BrowserEngine.MOZILLA);
		File manifestFile = new File(extPath, "manifest.json");
		try {
			System.out.println("\n[System] Preparing for first Mozilla Firefox setup...");
			File profileDir = new File(FIREFOX_PROFILE_PATH);
			prepareFirefoxProfile(profileDir);

			StringSelection stringSelection = new StringSelection(manifestFile.getAbsolutePath());
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);

			String preMessage = "This is the first time you use Hunting Mode with Mozilla Firefox!\n\n"
					+ "Firefox will open the debugging page.\n"
					+ "👉 The path to 'manifest.json' has been copied to your clipboard!\n"
					+ "👉 Click 'This Firefox' on the left menu.\n"
					+ "👉 Click 'Load Temporary Add-on...'\n"
					+ "👉 Paste or select the 'manifest.json' file.\n\n"
					+ "Press OK to launch Firefox.";
			JOptionPane.showMessageDialog(null, preMessage, "Step 1: Setting up Firefox Hunter",
					JOptionPane.INFORMATION_MESSAGE);

			ProcessBuilder pb = getFirefoxProcess("-profile", profileDir.getAbsolutePath(), "-no-remote",
					"about:debugging#/runtime/this-firefox");
			pb.start();

			String postMessage = "After loading the 'Video Hunter' add-on in Firefox,\n"
					+ "Click OK here to start hunting videos!";
			JOptionPane.showMessageDialog(null, postMessage, "Step 2: Verification", JOptionPane.INFORMATION_MESSAGE);

			new File(profileDir, "setup_done.txt").createNewFile();

			System.out.println("Firefox setup completed! Ready status.");
		} catch (Exception e) {
			System.err.println("Error Firefox setup: " + e.getMessage());
		}
	}

	public static void autoSetupExtension() {
		BrowserEngine engine = resolveActiveEngine();
		if (engine == BrowserEngine.MOZILLA) {
			autoSetupExtensionFirefox();
		} else {
			autoSetupExtensionChromium();
		}
	}

	public static void openCaptureBrowser(String url) {
		BrowserEngine engine = resolveActiveEngine();
		String targetUrl = url == null || url.trim().isEmpty() ? "https://www.google.com" : url.trim();

		if (engine == BrowserEngine.MOZILLA) {
			openFirefoxCapture(targetUrl);
		} else {
			openChromiumCapture(targetUrl);
		}
	}

	private static void openChromiumCapture(String targetUrl) {
		String extPath = ExtensionManager.getExtensionPath(BrowserEngine.CHROMIUM);
		File profileDir = new File(CHROME_PROFILE_PATH);
		File setupDone = new File(profileDir, "setup_done.txt");

		if (!setupDone.exists()) {
			autoSetupExtensionChromium();
		}

		try {
			System.out.println("Deploying Auto-Capture Browser (Chromium) to: " + targetUrl);
			ProcessBuilder pb = getChromiumProcess("--user-data-dir=" + CHROME_PROFILE_PATH,
					"--load-extension=" + extPath, "--no-first-run", "--no-default-browser-check", targetUrl);
			pb.start();
		} catch (Exception e) {
			System.err.println("Error launching Chromium: " + e.getMessage());
			JOptionPane.showMessageDialog(null,
					"Could not launch Chromium-based browser: " + e.getMessage()
							+ "\nPlease verify Chrome/Chromium is installed or switch engine to Mozilla Firefox.",
					"Browser Launch Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static void openFirefoxCapture(String targetUrl) {
		File profileDir = new File(FIREFOX_PROFILE_PATH);
		prepareFirefoxProfile(profileDir);
		File setupDone = new File(profileDir, "setup_done.txt");

		if (!setupDone.exists()) {
			autoSetupExtensionFirefox();
		}

		try {
			System.out.println("Deploying Auto-Capture Browser (Mozilla Firefox) to: " + targetUrl);
			ProcessBuilder pb = getFirefoxProcess("-profile", profileDir.getAbsolutePath(), "-no-remote",
					"-new-instance", targetUrl);
			pb.start();
		} catch (Exception e) {
			System.err.println("Error launching Firefox: " + e.getMessage());
			JOptionPane.showMessageDialog(null,
					"Could not launch Mozilla Firefox: " + e.getMessage()
							+ "\nPlease verify Firefox is installed or switch engine to Chromium.",
					"Browser Launch Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}