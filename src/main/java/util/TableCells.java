package util;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Callback;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Reusable modern table helpers + cell renderers for every admin screen. */
public final class TableCells {
    private TableCells() {}

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final String[] AVATAR_COLORS =
            {"#3b82f6", "#0ea5e9", "#14b8a6", "#8b5cf6", "#f59e0b", "#ef4444", "#64748b"};

    // ---------------------------------------------------------------- table setup

    /** Call once per TableView: row height, friendly empty message. */
    public static void modernize(TableView<?> tv) {
        tv.setFixedCellSize(56);
        tv.setFocusTraversable(false);
        Label ph = new Label("No records found");
        ph.getStyleClass().add("empty-state");
        tv.setPlaceholder(ph);
        fitColumns(tv);
    }

    // ---------------------------------------------------------------- column sizing

    private static final String UI_FONT = "Plus Jakarta Sans";
    private static final double MEASURE_SAFETY = 1.15; // fonts differ a little between machines

    /** Renderer wrapper that tells fitColumns how much room the cell needs beyond its text. */
    private static final class SizedFactory<S, T> implements Callback<TableColumn<S, T>, TableCell<S, T>> {
        private final Callback<TableColumn<S, T>, TableCell<S, T>> delegate;
        final double extra;        // avatar, pill padding, etc. added to the measured text
        final double fixedContent; // content that isn't read from the data (button labels)

        SizedFactory(Callback<TableColumn<S, T>, TableCell<S, T>> delegate, double extra, double fixedContent) {
            this.delegate = delegate;
            this.extra = extra;
            this.fixedContent = fixedContent;
        }

        @Override public TableCell<S, T> call(TableColumn<S, T> col) { return delegate.call(col); }
    }

    private static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> sized(
            Callback<TableColumn<S, T>, TableCell<S, T>> delegate, double extra, double fixedContent) {
        return new SizedFactory<>(delegate, extra, fixedContent);
    }

    private static double measure(String text, Font font) {
        Text t = new Text(text);
        t.setFont(font);
        return t.getLayoutBounds().getWidth() * MEASURE_SAFETY;
    }

    /**
     * Gives every column at least the width its header and its widest value need, so labels read
     * "BORROWER NAME" instead of "BORROWER NA...". Spare room is shared out evenly; when the columns
     * don't fit, the table scrolls sideways instead of cutting words off.
     * (modernize() already calls this - use it directly only for tables that don't call modernize.)
     */
    public static <S> void fitColumns(TableView<S> tv) {
        tv.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        Runnable fit = () -> applyFit(tv);
        InvalidationListener onData = o -> Platform.runLater(fit);

        tv.widthProperty().addListener(o -> fit.run());
        tv.itemsProperty().addListener((obs, oldItems, newItems) -> {
            if (oldItems != null) oldItems.removeListener(onData);
            if (newItems != null) newItems.addListener(onData);
            Platform.runLater(fit);
        });
        if (tv.getItems() != null) tv.getItems().addListener(onData);
        Platform.runLater(fit);
    }

    private static <S> void applyFit(TableView<S> tv) {
        javafx.collections.ObservableList<TableColumn<S, ?>> cols = tv.getColumns();
        if (cols.isEmpty()) return;

        Font headerFont = Font.font(UI_FONT, FontWeight.EXTRA_BOLD, 10.5);
        Font cellFont = Font.font(UI_FONT, FontWeight.BOLD, 13);
        int sample = tv.getItems() == null ? 0 : Math.min(tv.getItems().size(), 300);

        double[] need = new double[cols.size()];
        double total = 0;
        for (int c = 0; c < cols.size(); c++) {
            TableColumn<S, ?> col = cols.get(c);

            // header text + cell padding (16 + 16) + room for the sort arrow
            String header = col.getText() == null ? "" : col.getText().toUpperCase();
            double w = measure(header, headerFont) + 32 + 26;

            double extra = 0;
            double content = 0;
            Object factory = col.getCellFactory();
            if (factory instanceof SizedFactory<?, ?> sf) {
                extra = sf.extra;
                content = sf.fixedContent;
            }
            for (int i = 0; i < sample; i++) {
                ObservableValue<?> ov = col.getCellObservableValue(i);
                Object v = ov == null ? null : ov.getValue();
                if (v == null) continue;
                content = Math.max(content, measure(fmt(v), cellFont) + extra);
            }
            if (content > 0) w = Math.max(w, content + 32);

            // keep any minWidth the FXML asked for
            Double declared = (Double) col.getProperties().computeIfAbsent("fit-declared-min", k -> col.getMinWidth());
            w = Math.max(w, declared);

            need[c] = Math.ceil(w);
            total += need[c];
        }

        double available = tv.getWidth() - 20; // vertical scrollbar + borders
        double spare = available > total ? (available - total) / cols.size() : 0;
        for (int c = 0; c < cols.size(); c++) {
            TableColumn<S, ?> col = cols.get(c);
            col.setMinWidth(need[c]);
            col.setPrefWidth(need[c] + spare);
        }
    }

    /** Rounds the card's corners so the table doesn't poke out. */
    public static void clipRounded(Region card, double radius) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        card.setClip(clip);
    }

    /** Footer text: "24 records" or "Showing 3 of 24". */
    public static void bindCount(Label label, ObservableList<?> shown, ObservableList<?> all) {
        if (label == null) return;
        Runnable update = () -> label.setText(shown.size() == all.size()
                ? shown.size() + (shown.size() == 1 ? " record" : " records")
                : "Showing " + shown.size() + " of " + all.size());
        shown.addListener((Observable o) -> update.run());
        all.addListener((Observable o) -> update.run());
        update.run();
    }

    // ---------------------------------------------------------------- text cells

    private static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> labelCell(String styleClass, boolean dashIfNull) {
        return col -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || (item == null && !dashIfNull)) { setGraphic(null); return; }
                Label l = new Label(item == null ? "\u2014" : fmt(item));
                l.getStyleClass().add(styleClass);
                setGraphic(l);
            }
        };
    }

    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> idCell()      { return TableCells.<S, T>sized(TableCells.<S, T>labelCell("cell-id", false), 0, 0); }
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> primaryCell() { return TableCells.<S, T>sized(TableCells.<S, T>labelCell("cell-primary", false), 0, 0); }
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> mutedCell()   { return TableCells.<S, T>sized(TableCells.<S, T>labelCell("cell-muted", true), 0, 0); }
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> monoCell()    { return TableCells.<S, T>sized(TableCells.<S, T>labelCell("cell-mono", true), 0, 0); }
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> dateCell()    { return TableCells.<S, T>sized(TableCells.<S, T>labelCell("cell-muted", true), 0, 0); }

    /** Due date; red + bold when the predicate says the row is overdue. */
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> dueDateCell(Predicate<S> isOverdue) {
        Callback<TableColumn<S, T>, TableCell<S, T>> factory = col -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) { setGraphic(null); return; }
                Label l = new Label(fmt(item));
                @SuppressWarnings("unchecked")
                S row = getTableRow() == null ? null : (S) getTableRow().getItem();
                l.getStyleClass().add(row != null && isOverdue.test(row) ? "cell-overdue" : "cell-muted");
                setGraphic(l);
            }
        };
        return sized(factory, 0, 0);
    }

    /** Round initials avatar + name. */
    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> avatarNameCell() {
        Callback<TableColumn<S, String>, TableCell<S, String>> factory = col -> new TableCell<>() {
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                setText(null);
                if (empty || name == null || name.isBlank()) { setGraphic(null); return; }
                Label initials = new Label(initials(name));
                initials.getStyleClass().add("mini-avatar-text");
                StackPane av = new StackPane(initials);
                av.getStyleClass().add("mini-avatar");
                av.setStyle("-fx-background-color:" + AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length] + ";");
                Label n = new Label(name);
                n.getStyleClass().add("cell-primary");
                HBox box = new HBox(10, av, n);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        };
        return sized(factory, 40, 0); // 30px avatar + 10px gap
    }

    // ---------------------------------------------------------------- status pill

    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> statusPill() {
        Callback<TableColumn<S, String>, TableCell<S, String>> factory = col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) { setGraphic(null); return; }
                Label pill = new Label("\u25CF  " + item);
                pill.getStyleClass().addAll("pill", pillClass(item));
                setGraphic(pill);
            }
        };
        return sized(factory, 40, 0); // pill padding + the leading dot
    }

    // ---------------------------------------------------------------- action buttons

    public static <S> Callback<TableColumn<S, Void>, TableCell<S, Void>> actionButton(String text, Consumer<S> onClick) {
        return styledButton(text, onClick, "btn-table-action", r -> true);
    }

    /** Primary button, shown only on rows where showWhen is true. */
    public static <S> Callback<TableColumn<S, Void>, TableCell<S, Void>> actionButton(
            String text, Consumer<S> onClick, Predicate<S> showWhen) {
        return styledButton(text, onClick, "btn-table-action", showWhen);
    }

    /** Red ghost button (e.g. Remove). */
    public static <S> Callback<TableColumn<S, Void>, TableCell<S, Void>> dangerButton(String text, Consumer<S> onClick) {
        return styledButton(text, onClick, "btn-table-danger", r -> true);
    }

    private static <S> Callback<TableColumn<S, Void>, TableCell<S, Void>> styledButton(
            String text, Consumer<S> onClick, String styleClass, Predicate<S> showWhen) {
        Callback<TableColumn<S, Void>, TableCell<S, Void>> factory = col -> new TableCell<>() {
            private final Button btn = new Button(text);
            { btn.getStyleClass().add(styleClass);
                btn.setOnAction(e -> { S row = rowAt(this); if (row != null) onClick.accept(row); }); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                S row = empty ? null : rowAt(this);
                setGraphic(row != null && showWhen.test(row) ? btn : null);
            }
        };
        return sized(factory, 0, measure(text, Font.font(UI_FONT, FontWeight.BOLD, 12)) + 28);
    }

    /** Two buttons side by side: primary action + danger action (e.g. Approve / Reject). */
    public static <S> Callback<TableColumn<S, Void>, TableCell<S, Void>> dualActionButtons(
            String text1, Consumer<S> action1, String text2, Consumer<S> action2) {
        Callback<TableColumn<S, Void>, TableCell<S, Void>> factory = col -> new TableCell<>() {
            private final Button b1 = new Button(text1);
            private final Button b2 = new Button(text2);
            private final HBox box = new HBox(8, b1, b2);
            {
                b1.getStyleClass().add("btn-table-action");
                b2.getStyleClass().add("btn-table-danger");
                box.setAlignment(Pos.CENTER_LEFT);
                b1.setOnAction(e -> { S row = rowAt(this); if (row != null) action1.accept(row); });
                b2.setOnAction(e -> { S row = rowAt(this); if (row != null) action2.accept(row); });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setText(null);
                setGraphic(empty ? null : box);
            }
        };
        Font btnFont = Font.font(UI_FONT, FontWeight.BOLD, 12);
        return sized(factory, 0, measure(text1, btnFont) + measure(text2, btnFont) + 56 + 8);
    }

    // ---------------------------------------------------------------- internals

    private static <S> S rowAt(TableCell<S, ?> cell) {
        int i = cell.getIndex();
        if (cell.getTableView() == null || i < 0 || i >= cell.getTableView().getItems().size()) return null;
        return cell.getTableView().getItems().get(i);
    }

    public static String pillClass(String s) {
        String t = s.toLowerCase();
        if (t.contains("overdue") || t.contains("late") || t.contains("reject") || t.contains("damag")) return "pill-overdue";
        if (t.contains("maint") || t.contains("repair"))                                                 return "pill-maintenance";
        if (t.contains("pending") || t.contains("wait"))                                                 return "pill-pending";
        if (t.contains("checked") || t.contains("borrow") || t.contains("active") || t.contains("in use") || t.contains("approv")) return "pill-active";
        if (t.contains("return") || t.contains("available") || t.contains("done") || t.contains("complete")) return "pill-done";
        return "pill-default";
    }

    private static String fmt(Object o) {
        return (o instanceof LocalDate) ? ((LocalDate) o).format(DATE_FMT) : String.valueOf(o);
    }

    private static String initials(String name) {
        String[] p = name.trim().split("\\s+");
        String a = p[0].isEmpty() ? "?" : p[0].substring(0, 1);
        String b = p.length > 1 ? p[p.length - 1].substring(0, 1) : "";
        return (a + b).toUpperCase();
    }
}