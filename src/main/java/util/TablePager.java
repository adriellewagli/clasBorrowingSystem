package util;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TablePager<T> {

    public static final int PAGE_SIZE = 20;
    private static final String DEFAULT_SORT = "Default order";

    private final TableView<T> table;
    private final SortedList<T> source;
    private final ObservableList<T> pageItems = FXCollections.observableArrayList();
    private final Map<String, TableColumn<T, ?>> sortable = new LinkedHashMap<>();

    private final ComboBox<String> cmbSort = new ComboBox<>();
    private final Button btnDir = new Button();
    private final Button btnFirst = new Button("\u00AB");
    private final Button btnPrev = new Button("\u2039");
    private final Button btnNext = new Button("\u203A");
    private final Button btnLast = new Button("\u00BB");
    private final Label lblPage = new Label();
    private final HBox bar = new HBox(8);

    private int page = 0;
    private boolean ascending = true;
    private boolean syncing = false;

    public static <T> TablePager<T> install(TableView<T> table, FilteredList<T> filtered, SortedList<T> sorted) {
        return new TablePager<>(table, filtered, sorted);
    }

    private TablePager(TableView<T> table, FilteredList<T> filtered, SortedList<T> sorted) {
        this.table = table;
        this.source = sorted;

        for (TableColumn<T, ?> c : table.getColumns()) {
            if (c.isSortable() && c.getText() != null && !c.getText().isBlank()) {
                sortable.put(c.getText().trim(), c);
                c.sortTypeProperty().addListener(o -> syncFromTable());
            }
        }

        table.setSortPolicy(tv -> true);   // sorting is done by 'sorted' over ALL rows
        table.setItems(pageItems);         // table only shows the current page

        source.addListener((ListChangeListener<T>) c -> refreshPage());
        filtered.predicateProperty().addListener(o -> { page = 0; refreshPage(); });
        sorted.comparatorProperty().addListener(o -> { page = 0; refreshPage(); });
        table.getSortOrder().addListener((javafx.beans.InvalidationListener) o -> syncFromTable());

        buildBar();
        attach();
        refreshPage();
    }

    private void buildBar() {
        cmbSort.getItems().add(DEFAULT_SORT);
        cmbSort.getItems().addAll(sortable.keySet());
        cmbSort.setValue(DEFAULT_SORT);
        cmbSort.setPrefWidth(190);
        updateDirText();

        btnDir.getStyleClass().add("btn-secondary");
        for (Button b : List.of(btnFirst, btnPrev, btnNext, btnLast)) b.getStyleClass().add("btn-secondary");

        cmbSort.valueProperty().addListener((o, a, b) -> { if (!syncing) applySort(); });
        btnDir.setOnAction(e -> { ascending = !ascending; updateDirText(); if (!syncing) applySort(); });

        btnFirst.setOnAction(e -> goTo(0));
        btnPrev.setOnAction(e -> goTo(page - 1));
        btnNext.setOnAction(e -> goTo(page + 1));
        btnLast.setOnAction(e -> goTo(Integer.MAX_VALUE));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getStyleClass().add("table-footer-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 16, 8, 16));
        bar.getChildren().addAll(new Label("Sort by"), cmbSort, btnDir, spacer, lblPage,
                btnFirst, btnPrev, btnNext, btnLast);
    }

    private void attach() {
        Runnable add = () -> {
            if (table.getParent() instanceof Pane p && !p.getChildren().contains(bar)) {
                p.getChildren().add(p.getChildren().indexOf(table) + 1, bar);
            }
        };
        if (table.getParent() != null) add.run();
        else table.parentProperty().addListener((o, a, b) -> { if (b != null) add.run(); });
    }

    private void applySort() {
        syncing = true;
        try {
            TableColumn<T, ?> col = sortable.get(cmbSort.getValue());
            table.getSortOrder().clear();
            if (col != null) {
                col.setSortType(ascending ? TableColumn.SortType.ASCENDING : TableColumn.SortType.DESCENDING);
                table.getSortOrder().add(col);
            }
        } finally {
            syncing = false;
        }
    }

    /** Header clicks keep the combo box and direction button in step. */
    private void syncFromTable() {
        if (syncing) return;
        syncing = true;
        try {
            if (table.getSortOrder().isEmpty()) {
                cmbSort.setValue(DEFAULT_SORT);
            } else {
                TableColumn<T, ?> col = table.getSortOrder().get(0);
                cmbSort.setValue(col.getText() == null ? DEFAULT_SORT : col.getText().trim());
                ascending = col.getSortType() == TableColumn.SortType.ASCENDING;
            }
            updateDirText();
        } finally {
            syncing = false;
        }
    }

    private void updateDirText() {
        btnDir.setText(ascending ? "\u25B2 Ascending" : "\u25BC Descending");
    }

    private void goTo(int p) {
        page = p;
        refreshPage();
    }

    private void refreshPage() {
        int total = source.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * PAGE_SIZE;
        int to = Math.min(total, from + PAGE_SIZE);

        List<T> slice = new ArrayList<>(source.subList(from, to));
        if (!sameRefs(pageItems, slice)) pageItems.setAll(slice);

        lblPage.setText(total == 0 ? "No items"
                : "Showing " + (from + 1) + "\u2013" + to + " of " + total + "  \u2022  Page " + (page + 1) + " / " + pages);
        btnFirst.setDisable(page == 0);
        btnPrev.setDisable(page == 0);
        btnNext.setDisable(page >= pages - 1);
        btnLast.setDisable(page >= pages - 1);
    }

    private static <E> boolean sameRefs(List<E> a, List<E> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }
}