package dao;

import model.BorrowedItem;
import model.DamageReport;
import model.Equipment;
import model.ItemFee;
import model.ItemUsage;
import model.Receipt;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface EquipmentDAO {

    /**
     * Default late penalty per day. Each item has its own rate (set by the Super Admin in the
     * Fees & Penalties tab); this is only the starting value for new items and the fallback.
     */
    double LATE_PENALTY_PER_DAY = 50.00;

    boolean addEquipment(Equipment equipment);

    /** Fetch all equipment items from database */
    List<Equipment> getAllEquipment();

    /** Fetch equipment filtered by status (e.g. "Available", "Checked Out") */
    List<Equipment> getEquipmentByStatus(String status);

    /** Update equipment status in database */
    boolean updateStatus(int equipmentId, String newStatus);

    /** Record a single borrow transaction */
    boolean createBorrowRequest(
            int equipmentId,
            String borrowerName,
            String borrowerType,
            String borrowerIdNumber,
            String programOrDept,
            LocalDate returnTargetDate,
            int processedBy,
            double totalFeeCharged,
            byte[] idSnapshotBytes
    );

    /**
     * Older entry point: one return date for every item. The fee argument is ignored now - each item's
     * borrowing fee is set by the Super Admin.
     */
    boolean createBatchBorrowRequest(
            List<Integer> equipmentIds,
            String borrowerName,
            String borrowerType,
            String borrowerIdNumber,
            String programOrDept,
            LocalDate returnTargetDate,
            int processedBy,
            double totalFeeChargedPerItem,
            byte[] idSnapshotBytes
    );

    /**
     * Record a borrow request where EVERY item has its own return date (equipment id -> due date).
     * Each item becomes its own PENDING transaction; the item's fees are applied when an admin approves it.
     */
    boolean createBatchBorrowRequest(
            List<Integer> equipmentIds,
            Map<Integer, LocalDate> returnDates,
            String borrowerName,
            String borrowerType,
            String borrowerIdNumber,
            String programOrDept,
            int requestedByUserId,
            byte[] idSnapshotBytes
    );

    /**
     * Same as the Map version of createBatchBorrowRequest, but explains a refusal.
     * Duplicate ids are ignored, and an item that already has a waiting request or is checked out
     * (or in maintenance) is refused, so the same item can never be requested twice.
     * @return null on success, otherwise a message explaining why nothing was saved
     */
    String submitBatchBorrowRequest(
            List<Integer> equipmentIds,
            Map<Integer, LocalDate> returnDates,
            String borrowerName,
            String borrowerType,
            String borrowerIdNumber,
            String programOrDept,
            int requestedByUserId,
            byte[] idSnapshotBytes
    );

    /**
     * Reserve an item for a future date.
     * @return null on success, otherwise a message explaining why the reservation was refused
     */
    String createReservation(
            int equipmentId,
            String borrowerName,
            String borrowerType,
            String borrowerIdNumber,
            String programOrDept,
            LocalDate pickupDate,
            LocalDate returnDate,
            int requestedByUserId,
            double totalFee,
            byte[] idSnapshotBytes
    );

    /** First day this item can be reserved for (after anything already out or requested). */
    LocalDate getEarliestReservationDate(int equipmentId);

    /** Staff side: the user's requests that are still waiting for an admin (pending + reservations). */
    List<BorrowedItem> getPendingRequestsForUser(int userId);

    /** Staff side: cancel one of the user's own requests while it is still waiting. */
    /** Like cancelRequest, but says WHY it failed: null = cancelled, otherwise a message for the user. */
    default String cancelRequestResult(int transactionId, int userId) {
        return cancelRequest(transactionId, userId) ? null : "Could not cancel the request.";
    }

    boolean cancelRequest(int transactionId, int userId);

    /** Number of requests waiting for an admin (pending borrows + reservations). */
    int countPendingRequests();

    /** Fetch active borrow transactions for a specific user */
    List<BorrowedItem> getActiveBorrowsForUser(int userId);

    /** Fetch complete borrow transaction history for a specific user */
    List<BorrowedItem> getBorrowHistoryForUser(int userId);

    /**
     * User side: flag an active borrow as "PENDING RETURN" so it shows up in the admin's
     * Process Returns tab. Only the user who requested the borrow can do this.
     */
    boolean requestReturn(int transactionId, int userId);

    /** Same as the longer version, without a condition note or damage report. */
    boolean processReturnRequest(int transactionId, int adminUserId, boolean sendToMaintenance);

    /**
     * Admin side: finish a return that the user has already requested. Also records the late penalty.
     * @param sendToMaintenance true when the item came back worn or damaged - the equipment is then
     *                          set to MAINTENANCE (shows in the Maintenance Log) instead of AVAILABLE.
     * @param condition         condition recorded on return (may be null)
     * @param damageReport      narrative of the specific damage (may be null)
     */
    boolean processReturnRequest(int transactionId, int adminUserId, boolean sendToMaintenance,
                                 String condition, String damageReport);

    /** Latest damage narrative written for this item (null if none). */
    DamageReport getLatestDamageReport(int equipmentId);

    /** Admin Process Returns tab: only items whose user has clicked "Return item" */
    List<BorrowedItem> getPendingReturnTransactions();

    /** Digital receipt data for one transaction (null if the transaction doesn't exist) */
    Receipt getReceiptForTransaction(int transactionId);

    /** Fetch all active borrow transactions for the Admin dashboard */
    List<BorrowedItem> getAllActiveTransactions();

    /** Fetch all pending transactions (borrows and reservations) for the Admin Manage Requests view */
    List<BorrowedItem> getPendingTransactions();

    /** Complete audit log of all transactions, the most recently approved one first */
    List<BorrowedItem> getAllTransactionHistory();

    /** Items ranked by how many times they have been borrowed (most used first). */
    List<ItemUsage> getMostUsedEquipment();

    /** Approve a pending request. Kept for older callers - same as {@link #approveBorrow}. */
    boolean approveBorrowRequest(int transactionId, int equipmentId, int adminUserId);

    /**
     * Approve a pending request or reservation. The borrowing fee and the late fee per day are copied
     * from the item's current settings (Fees & Penalties tab) onto the transaction, so changing the
     * fees later does not affect items that are already out.
     * @return null on success, otherwise a message explaining why it could not be approved
     */
    String approveBorrow(int transactionId, int equipmentId, int adminUserId);

    /** Super Admin: every item with its borrowing fee and late fee per day. */
    List<ItemFee> getItemFees();

    /**
     * Super Admin: set one item's borrowing fee and late fee per day (0 is allowed). Applies to requests
     * approved from now on; items already borrowed keep the fees they were approved with.
     * @return null on success, otherwise a message explaining why it could not be saved
     */
    String updateItemFees(int equipmentId, double borrowFee, double lateFeePerDay);

    /** Reject a pending borrow request and record the rejecting admin ID */
    boolean rejectBorrowRequest(int transactionId, int equipmentId, int adminUserId);
}