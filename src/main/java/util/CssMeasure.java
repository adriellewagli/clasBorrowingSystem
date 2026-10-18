package util;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Measures how wide a piece of table content really is, by laying it out with the app's own
 * stylesheet (font, weight, padding, pill shape...) in an off-screen scene. Estimating with a
 * hard-coded font is what used to cut words off ("Laptops & Comput...") when the real font
 * rendered wider than the guess.
 *
 * Must be called on the JavaFX thread.
 */
final class CssMeasure {
    private CssMeasure() {}

    private static final String STYLESHEET = "/com/borrowclas/clasborrowingsystem/css/dashboard.css";
    private static final double SAFETY = 6; // sub-pixel rounding + font differences between machines

    private static Scene scene;
    private static StackPane tableCell;   // .root-canvas > .table-view > .table-cell
    private static StackPane columnHeader; // .root-canvas > .table-view > .column-header
    private static final Map<String, Double> cache = new HashMap<>();

    /** Forget cached widths (call after the UI font finishes loading). */
    static void invalidate() {
        cache.clear();
        scene = null;
    }

    private static void ensureScene() {
        if (scene != null) return;
        StackPane root = new StackPane();
        root.getStyleClass().add("root-canvas");
        URL css = CssMeasure.class.getResource(STYLESHEET);
        if (css != null) root.getStylesheets().add(css.toExternalForm());

        StackPane tv = new StackPane();
        tv.getStyleClass().add("table-view");
        tableCell = new StackPane();
        tableCell.getStyleClass().add("table-cell");
        columnHeader = new StackPane();
        columnHeader.getStyleClass().add("column-header");
        tv.getChildren().addAll(tableCell, columnHeader);
        root.getChildren().add(tv);

        scene = new Scene(root, 800, 600);
    }

    /** Width of a label inside a table cell (content only - the cell's own padding is not included). */
    static double label(String text, String... styleClasses) {
        String key = "L|" + String.join(",", styleClasses) + "|" + text;
        Double hit = cache.get(key);
        if (hit != null) return hit;

        ensureScene();
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        tableCell.getChildren().setAll(l);
        l.applyCss();
        double w = Math.ceil(l.prefWidth(-1)) + SAFETY;
        cache.put(key, w);
        return w;
    }

    /** Width of a button inside a table cell. */
    static double button(String text, String styleClass) {
        String key = "B|" + styleClass + "|" + text;
        Double hit = cache.get(key);
        if (hit != null) return hit;

        ensureScene();
        Button b = new Button(text);
        b.getStyleClass().add(styleClass);
        tableCell.getChildren().setAll(b);
        b.applyCss();
        double w = Math.ceil(b.prefWidth(-1)) + SAFETY;
        cache.put(key, w);
        return w;
    }

    /** Width a column header needs (text only - the sort arrow and cell padding are added by the caller). */
    static double header(String text) {
        String key = "H|" + text;
        Double hit = cache.get(key);
        if (hit != null) return hit;

        ensureScene();
        Label l = new Label(text);
        columnHeader.getChildren().setAll(l);
        l.applyCss();
        double w = Math.ceil(l.prefWidth(-1)) + SAFETY;
        cache.put(key, w);
        return w;   
    }
}
