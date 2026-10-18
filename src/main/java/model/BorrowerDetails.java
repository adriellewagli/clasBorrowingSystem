package model;

/**
 * Who borrowed an item: the details typed on the borrow form plus the ID photo taken then.
 * Only the Super Admin can load this (see EquipmentDAO.getBorrowerDetails).
 */
public class BorrowerDetails {
    private final int transactionId;
    private final String borrowerName;
    private final String borrowerType;      // STUDENT or FACULTY
    private final String borrowerIdNumber;
    private final String programOrDept;
    private final String requestedBy;       // username of the account that submitted the request
    private final byte[] idSnapshot;        // JPEG bytes, or null if no photo was taken

    public BorrowerDetails(int transactionId, String borrowerName, String borrowerType, String borrowerIdNumber,
                           String programOrDept, String requestedBy, byte[] idSnapshot) {
        this.transactionId = transactionId;
        this.borrowerName = borrowerName;
        this.borrowerType = borrowerType;
        this.borrowerIdNumber = borrowerIdNumber;
        this.programOrDept = programOrDept;
        this.requestedBy = requestedBy;
        this.idSnapshot = idSnapshot;
    }

    public int getTransactionId() { return transactionId; }
    public String getBorrowerName() { return dash(borrowerName); }
    public String getBorrowerIdNumber() { return dash(borrowerIdNumber); }
    public String getProgramOrDept() { return dash(programOrDept); }
    public String getRequestedBy() { return dash(requestedBy); }
    public byte[] getIdSnapshot() { return idSnapshot; }

    public boolean isStudent() { return !"FACULTY".equalsIgnoreCase(borrowerType == null ? "" : borrowerType.trim()); }

    /** "Student" or "Faculty". */
    public String getTypeLabel() { return isStudent() ? "Student" : "Faculty"; }

    public boolean hasIdPhoto() { return idSnapshot != null && idSnapshot.length > 0; }

    private static String dash(String s) {
        return (s == null || s.isBlank()) ? "\u2014" : s.trim();
    }
}