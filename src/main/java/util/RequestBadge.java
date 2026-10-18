package util;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * The number of requests waiting for an admin (pending borrows + reservations).
 * The admin sidebar shows it beside "Manage Requests"; the dashboard refreshes it every few
 * seconds and Manage Requests updates it right after an approval or rejection.
 */
public final class RequestBadge {
    public static final IntegerProperty PENDING = new SimpleIntegerProperty(0);

    private RequestBadge() {}
}
