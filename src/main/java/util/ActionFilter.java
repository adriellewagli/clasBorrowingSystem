package util;

import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * "Filter by action" drop-down for a table.
 * The choices are built from what is actually in the table (Pending, Overdue, Returned ...),
 * and the filter works together with the search box: call {@link #setSearch(Predicate)} from the
 * search listener instead of setting the FilteredList predicate yourself.
 */
public final class ActionFilter<T> {

    public static final String ALL = "All actions";

    private final ComboBox<String> combo;
    private final ObservableList<T> source;
    private final FilteredList<T> list;
    private final Function<T, String> key;

    private Predicate<T> search = t -> true;
    private boolean rebuilding = false;

    public ActionFilter(ComboBox<String> combo, ObservableList<T> source,
                        FilteredList<T> list, Function<T, String> key) {
        this.combo = combo;
        this.source = source;
        this.list = list;
        this.key = key;

        if (combo != null) {
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setPrefHeight(40);
            combo.setPrefWidth(190);
            combo.valueProperty().addListener((obs, o, n) -> { if (!rebuilding) apply(); });
        }
        // IMPORTANT: this must be a ListChangeListener, not an InvalidationListener. JavaFX fires
        // invalidation listeners BEFORE change listeners, so the FilteredList would still be in the
        // middle of handling the same change when rebuild() re-filtered it - and then applied the
        // change a second time, showing every row twice ("Showing 4 of 2"). A change listener
        // registered after the FilteredList runs after it has finished.
        source.addListener((javafx.collections.ListChangeListener<T>) c -> rebuild());
        rebuild();
    }

    /** Plug the search box in here. Pass null for "no search". */
    public void setSearch(Predicate<T> p) {
        this.search = p == null ? t -> true : p;
        apply();
    }

    private Predicate<T> actionPredicate() {
        String sel = combo == null ? null : combo.getValue();
        if (sel == null || ALL.equals(sel)) return t -> true;
        return t -> sel.equalsIgnoreCase(key.apply(t));
    }

    private void apply() {
        list.setPredicate(search.and(actionPredicate()));
    }

    private void rebuild() {
        if (combo == null) return;
        rebuilding = true;
        try {
            String previous = combo.getValue();
            Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (T t : source) {
                String k = key.apply(t);
                if (k != null && !k.isBlank()) values.add(k);
            }
            List<String> items = new ArrayList<>();
            items.add(ALL);
            items.addAll(values);
            combo.setItems(FXCollections.observableArrayList(items));
            combo.setValue(previous != null && items.contains(previous) ? previous : ALL);
        } finally {
            rebuilding = false;
        }
        apply();
    }
}
