package dao;

import model.BorrowedItem;
import model.Equipment;
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

    /** Process an item return request and record the staff/admin who handled it */
    boolean processReturnRequest(int transactionId, int equipmentId, int adminUserId);

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