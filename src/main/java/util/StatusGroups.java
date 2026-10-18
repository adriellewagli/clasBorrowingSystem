package util;

/** Maps the filter chips (Pending / Active / Rejected ...) to the exact status labels in the tables. */
public final class StatusGroups {
    private StatusGroups() {}

    /** @param group null means "All" */
    public static boolean matches(String group, String status) {
        if (group == null) return true;
        if (status == null) return false;
        String s = status.trim().toLowerCase();
        return switch (group) {
            case "Pending"   -> s.equals("pending") || s.equals("reserved");
            case "Active", "Approved" -> s.equals("active") || s.equals("approved")
                    || s.equals("overdue") || s.equals("pending return");
            case "Rejected"  -> s.equals("rejected");
            case "Returned"  -> s.equals("returned");
            case "Cancelled" -> s.equals("cancelled");
            default          -> s.equalsIgnoreCase(group);
        };
    }
}
