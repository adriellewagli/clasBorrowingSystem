package util;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import db.DbHealth;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.util.Duration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Keeps a screen in step with the database: while the screen is on display it re-reads the data
 * every few seconds (in the background, so the window never freezes) and updates the table only
 * when something actually changed - so rows don't flicker, and an approval made on another
 * computer shows up without pressing Refresh or switching tabs.
 */
public final class LiveRefresh {
    private LiveRefresh() {}

    public static final double DEFAULT_SECONDS = 4;

    /**
     * Bumped whenever the app itself changes data (e.g. the user presses "Return item"). A background
     * read that started before the bump is thrown away, so it can't paint the old rows back on screen.
     */
    private static final java.util.concurrent.atomic.AtomicLong CHANGE_STAMP = new java.util.concurrent.atomic.AtomicLong();

    public static void markChanged() { CHANGE_STAMP.incrementAndGet(); }

    /** Lets the owner stop a watcher early (e.g. on log out). */
    public interface Handle { void stop(); }

    /**
     * Calls {@code tick} on the JavaFX thread every {@code seconds}, but only while {@code anchor}
     * is on screen. If {@code stopWhenDetached} is true the watcher ends for good the first time
     * the anchor leaves the scene (use it for screens that are loaded fresh on every visit).
     */
    public static Handle watch(Node anchor, double seconds, boolean stopWhenDetached, Runnable tick) {
        Timeline timeline = new Timeline();
        boolean[] seen = {false};
        int[] idleTicks = {0};

        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(seconds), e -> {
            if (anchor.getScene() == null) {
                // never shown (failed to load): give up after a while
                if (!seen[0] && ++idleTicks[0] > 15) timeline.stop();
                return;
            }
            seen[0] = true;
            tick.run();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        anchor.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) seen[0] = true;
            else if (seen[0] && stopWhenDetached) timeline.stop();
        });

        timeline.play();
        return timeline::stop;
    }

    /**
     * Watches a table's data. {@code fetch} reads the rows (runs off the JavaFX thread - database
     * work only, never touch the UI in it). When the rows differ from what {@code target} holds,
     * the target is replaced and {@code afterApply} runs (update counters, charts...).
     */
    public static <T> Handle poll(Node anchor, ObservableList<T> target, Supplier<List<T>> fetch,
                                  Runnable afterApply, boolean stopWhenDetached) {
        AtomicBoolean busy = new AtomicBoolean(false);
        return watch(anchor, DEFAULT_SECONDS, stopWhenDetached, () -> {
            if (!busy.compareAndSet(false, true)) return; // previous read still running
            final long stampAtStart = CHANGE_STAMP.get();
            Task<List<T>> task = new Task<>() {
                @Override protected List<T> call() {
                    DbHealth.reset();
                    List<T> rows = fetch.get();
                    return DbHealth.failed() ? null : rows; // null = the read failed, keep what is on screen
                }
            };
            task.setOnSucceeded(e -> {
                busy.set(false);
                List<T> fresh = task.getValue();
                if (anchor.getScene() == null) return;
                if (stampAtStart != CHANGE_STAMP.get()) return; // data changed while reading: this read is stale
                if (fresh == null) { DbHealth.OFFLINE.set(true); return; }
                DbHealth.OFFLINE.set(false);
                if (!fingerprint(fresh).equals(fingerprint(target))) {
                    target.setAll(fresh);
                    if (afterApply != null) afterApply.run();
                }
            });
            task.setOnFailed(e -> busy.set(false));
            Thread t = new Thread(task, "live-refresh");
            t.setDaemon(true);
            t.start();
        });
    }

    public static <T> Handle poll(Node anchor, ObservableList<T> target, Supplier<List<T>> fetch,
                                  Runnable afterApply) {
        return poll(anchor, target, fetch, afterApply, true);
    }

    /**
     * Wires a Refresh button: reads in the background, always replaces the rows, redraws the table
     * and gives visible feedback (button shows "Refreshing..." then "Updated hh:mm:ss").
     */
    public static <T> void manualRefresh(javafx.scene.control.Button button,
                                         javafx.scene.control.TableView<T> table,
                                         ObservableList<T> target, Supplier<List<T>> fetch,
                                         Runnable afterApply) {
        String original = button == null ? null : button.getText();
        if (button != null) { button.setDisable(true); button.setText("Refreshing..."); }
        long started = System.currentTimeMillis();
        background(fetch, rows -> {
            if (rows != null) {
                target.setAll(rows);
                if (table != null) table.refresh();
                if (afterApply != null) afterApply.run();
            }
            if (button == null) return;
            // keep the "Refreshing..." state visible long enough to be noticed
            long wait = Math.max(0, 450 - (System.currentTimeMillis() - started));
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.millis(wait));
            pause.setOnFinished(e -> {
                button.setDisable(false);
                button.setText(rows == null ? "Failed - retry" : "Updated " + java.time.LocalTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                javafx.animation.PauseTransition reset = new javafx.animation.PauseTransition(Duration.seconds(3));
                reset.setOnFinished(ev -> button.setText(original));
                reset.play();
            });
            pause.play();
        });
    }

    /**
     * Runs {@code work} off the JavaFX thread, then {@code apply} with its result on the JavaFX thread.
     * {@code apply} receives null if the work failed.
     */
    public static <R> void background(Supplier<R> work, java.util.function.Consumer<R> apply) {
        Task<R> task = new Task<>() {
            @Override protected R call() {
                DbHealth.reset();
                R result = work.get();
                return DbHealth.failed() ? null : result; // null = the read failed
            }
        };
        task.setOnSucceeded(e -> {
            DbHealth.OFFLINE.set(task.getValue() == null);
            apply.accept(task.getValue());
        });
        task.setOnFailed(e -> apply.accept(null));
        Thread t = new Thread(task, "live-refresh");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ change detection

    private static final Map<Class<?>, List<Method>> GETTERS = new ConcurrentHashMap<>();

    /** A text summary of every simple value (text, numbers, dates, flags) the rows expose through getters. */
    public static String fingerprint(List<?> rows) {
        StringBuilder sb = new StringBuilder();
        for (Object row : new ArrayList<>(rows)) {
            if (row == null) { sb.append("null\n"); continue; }
            for (Method m : gettersOf(row.getClass())) {
                try {
                    sb.append(m.invoke(row)).append('|');
                } catch (Exception ex) {
                    sb.append('?').append('|');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static List<Method> gettersOf(Class<?> type) {
        return GETTERS.computeIfAbsent(type, c -> {
            List<Method> list = new ArrayList<>();
            for (Method m : c.getMethods()) {
                String n = m.getName();
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
                if (!(n.startsWith("get") || n.startsWith("is")) || n.equals("getClass")) continue;
                Class<?> r = m.getReturnType();
                boolean simple = r.isPrimitive() || r == String.class || Number.class.isAssignableFrom(r)
                        || r == Boolean.class || r.isEnum() || TemporalAccessor.class.isAssignableFrom(r);
                if (simple) list.add(m);
            }
            list.sort((a, b) -> a.getName().compareTo(b.getName()));
            return list;
        });
    }
}
