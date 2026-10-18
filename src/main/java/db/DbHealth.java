package db;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Tells the screens whether the last database read worked.
 * A DAO read method calls {@link #readFailed()} when it could not read; the background refresh then
 * keeps the table's current rows (instead of replacing them with an empty list) and {@link #OFFLINE}
 * switches on the "can't reach the database" banner until a read succeeds again.
 */
public final class DbHealth {
    private DbHealth() {}

    /** True while the latest background read failed. Only change it on the JavaFX thread. */
    public static final BooleanProperty OFFLINE = new SimpleBooleanProperty(false);

    private static final ThreadLocal<boolean[]> FAILED = ThreadLocal.withInitial(() -> new boolean[1]);

    /** Called by a DAO read method from its catch block. */
    public static void readFailed() { FAILED.get()[0] = true; }

    /** Call on the reading thread before a read, then check {@link #failed()} after it. */
    public static void reset() { FAILED.get()[0] = false; }

    public static boolean failed() { return FAILED.get()[0]; }
}
