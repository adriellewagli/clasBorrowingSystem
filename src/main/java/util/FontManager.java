package util;
import javafx.application.Platform;

import javafx.scene.Scene;
import javafx.scene.text.Font;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Offline-first font loader.
 *  1) bundled fonts in /resources/fonts (optional)
 *  2) fonts cached in ~/.clas/fonts (from an earlier online run)
 *  3) if online: download Plus Jakarta Sans from Google Fonts in the
 *     background, cache it, and refresh the UI. Never blocks startup.
 * If everything fails, CSS falls back to Segoe UI / system fonts.
 *
 * Usage (after creating the Scene):  FontManager.install(scene);
 */
public final class FontManager {
    private static final Path CACHE = Paths.get(System.getProperty("user.home"), ".clas", "fonts");
    private static final String CSS_URL =
            "https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap";

    private FontManager() {}

    public static void install(Scene scene) {
        boolean cached = loadDir(CACHE);
        loadBundled();
        if (!cached) {
            Thread t = new Thread(() -> {
                if (download()) {
                    Platform.runLater(() -> { loadDir(CACHE); reapplyCss(scene); });
                }
            }, "font-fetch");
            t.setDaemon(true);
            t.start();
        }
    }

    private static boolean loadDir(Path dir) {
        boolean any = false;
        if (!Files.isDirectory(dir)) return false;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.ttf")) {
            for (Path p : ds) {
                try (InputStream in = Files.newInputStream(p)) {
                    any |= Font.loadFont(in, 13) != null;
                }
            }
        } catch (Exception ignored) {}
        return any;
    }

    private static void loadBundled() {
        for (String n : new String[]{"PlusJakartaSans-Regular", "PlusJakartaSans-Medium",
                "PlusJakartaSans-SemiBold", "PlusJakartaSans-Bold", "PlusJakartaSans-ExtraBold"}) {
            try (InputStream in = FontManager.class.getResourceAsStream("/fonts/" + n + ".ttf")) {
                if (in != null) Font.loadFont(in, 13);
            } catch (Exception ignored) {}
        }
    }

    private static boolean download() {
        try {
            String css = fetch(CSS_URL);               // no User-Agent => TTF links
            Matcher m = Pattern.compile("url\\((https://[^)]+)\\)").matcher(css);
            Set<String> urls = new LinkedHashSet<>();
            while (m.find()) urls.add(m.group(1));
            if (urls.isEmpty()) return false;
            Files.createDirectories(CACHE);
            int i = 0;
            for (String u : urls) {
                try (InputStream in = open(u)) {
                    Files.copy(in, CACHE.resolve("pjs-" + (i++) + ".ttf"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        } catch (Exception offline) {
            return false;                               // offline: silently use fallback
        }
    }

    private static InputStream open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(2500);
        c.setReadTimeout(4000);
        return c.getInputStream();
    }

    private static String fetch(String url) throws Exception {
        try (InputStream in = open(url)) {
            return new String(in.readAllBytes());
        }
    }

    private static void reapplyCss(Scene scene) {
        // the stylesheet may be set on the scene or on the FXML root node
        refresh(scene.getStylesheets());
        if (scene.getRoot() != null) refresh(scene.getRoot().getStylesheets());
    }

    private static void refresh(javafx.collections.ObservableList<String> sheets) {
        List<String> copy = new ArrayList<>(sheets);
        sheets.clear();
        sheets.addAll(copy);
    }
}