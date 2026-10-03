package util;

import javafx.beans.Observable;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
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

    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> idCell()      { return labelCell("cell-id", false); }
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> primaryCell() { return labelCell("cell-primary", false); }
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> mutedCell()   { return labelCell("cell-muted", true); }
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> monoCell()    { return labelCell("cell-mono", true); }
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> dateCell()    { return labelCell("cell-muted", true); }

    /** Due date; red + bold when the predicate says the row is overdue. */
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> dueDateCell(Predicate<S> isOverdue) {
        return col -> new TableCell<>() {
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
    }

    /** Round initials avatar + name. */
    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> avatarNameCell() {
        return col -> new TableCell<>() {
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
    }

    // ---------------------------------------------------------------- status pill

    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> statusPill() {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) { setGraphic(null); return; }
                Label pill = new Label("\u25CF  " + item);
                pill.getStyleClass().addAll("pill", pillClass(item));
                setGraphic(pill);
            }
        };
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
        return col -> new TableCell<>() {
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
    }

    /** Two buttons side by side: primary action + danger action (e.g. Approve / Reject). */
    public static <S> Callback<TableColumn<S, Void>, TableCell<S, Void>> dualActionButtons(
            String text1, Consumer<S> action1, String text2, Consumer<S> action2) {
        return col -> new TableCell<>() {
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
    }

    // ---------------------------------------------------------------- internals

    private static <S> S rowAt(TableCell<S, ?> cell) {
        int i = cell.getIndex();
        if (cell.getTableView() == null || i < 0 || i >= cell.getTableView().getItems().size()) return null;
        return cell.getTableView().getItems().get(i);
    }

    private static String pillClass(String s) {
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