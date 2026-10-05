package dao;

import model.BorrowedItem;
import model.Equipment;
import model.Receipt;
import java.time.LocalDate;
import java.util.List;

public interface EquipmentDAO {

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

    /** Record a batch borrow request for multiple items in a single transaction */
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

    /** Fetch active borrow transactions for a specific user */
    List<BorrowedItem> getActiveBorrowsForUser(int userId);

    /** Fetch complete borrow transaction history for a specific user */
    List<BorrowedItem> getBorrowHistoryForUser(int userId);

    /**
     * User side: flag an active borrow as "PENDING RETURN" so it shows up in the admin's
     * Process Returns tab. Only the user who requested the borrow can do this.
     */
    boolean requestReturn(int transactionId, int userId);

    /**
     * Admin side: finish a return that the user has already requested.
     * @param sendToMaintenance true when the item came back worn or damaged - the equipment is then
     *                          set to MAINTENANCE (shows in the Maintenance Log) instead of AVAILABLE.
     */
    boolean processReturnRequest(int transactionId, int adminUserId, boolean sendToMaintenance);

    /** Admin Process Returns tab: only items whose user has clicked "Return item" */
    List<BorrowedItem> getPendingReturnTransactions();

    /** Digital receipt data for one transaction (null if the transaction doesn't exist) */
    Receipt getReceiptForTransaction(int transactionId);

    /** Fetch all active borrow transactions for the Admin dashboard */
    List<BorrowedItem> getAllActiveTransactions();

    /** Fetch all pending transactions for the Admin Manage Requests view */
    List<BorrowedItem> getPendingTransactions();

    /** Fetch complete historical audit log of all transactions (Approved, Returned, Rejected) */
    List<BorrowedItem> getAllTransactionHistory();

    /** Approve a pending borrow request and record the approving admin ID */
    boolean approveBorrowRequest(int transactionId, int equipmentId, int adminUserId);

    /** Reject a pending borrow request and record the rejecting admin ID */
    boolean rejectBorrowRequest(int transactionId, int equipmentId, int adminUserId);
}