package dao;

import model.BorrowedItem;
import model.BorrowerDetails;
import model.DamageReport;
import model.Equipment;
import model.ItemFee;
import model.ItemUsage;
import model.PasswordTicket;
import model.Receipt;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


public interface EquipmentDAO {

    /**
     * Who borrowed it: the borrower's details and ID photo for one transaction. Personal data - this only
     * works while a Super Admin is logged in; for anyone else it returns null.
     */
    BorrowerDetails getBorrowerDetails(int transactionId);

    /** Default late penalty per day. */
    double LATE_PENALTY_PER_DAY = 50.00;

    boolean addEquipment(Equipment equipment);
    List<Equipment> getAllEquipment();
    List<Equipment> getEquipmentByStatus(String status);
    boolean updateStatus(int equipmentId, String newStatus);

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

    /** Same as above, with the price of each item (equipment id -> price) entered with the request. */
    String submitBatchBorrowRequest(
            List<Integer> equipmentIds,
            Map<Integer, LocalDate> returnDates,
            Map<Integer, Double> prices,
            String borrowerName,
            String borrowerType,
            String borrowerIdNumber,
            String programOrDept,
            int requestedByUserId,
            byte[] idSnapshotBytes
    );

    /** Periods ({start, end}) in which the item is already out, requested or reserved, from today on. */
    List<LocalDate[]> getBookedRanges(int equipmentId);

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

    LocalDate getEarliestReservationDate(int equipmentId);
    List<BorrowedItem> getPendingRequestsForUser(int userId);

    default String cancelRequestResult(int transactionId, int userId) {
        return cancelRequest(transactionId, userId) ? null : "Could not cancel the request.";
    }

    boolean cancelRequest(int transactionId, int userId);
    int countPendingRequests();

    List<BorrowedItem> getActiveBorrowsForUser(int userId);
    List<BorrowedItem> getBorrowHistoryForUser(int userId);

    boolean requestReturn(int transactionId, int userId);
    boolean processReturnRequest(int transactionId, int adminUserId, boolean sendToMaintenance);
    boolean processReturnRequest(int transactionId, int adminUserId, boolean sendToMaintenance,
                                 String condition, String damageReport);

    DamageReport getLatestDamageReport(int equipmentId);
    List<BorrowedItem> getPendingReturnTransactions();
    Receipt getReceiptForTransaction(int transactionId);
    List<BorrowedItem> getAllActiveTransactions();
    List<BorrowedItem> getPendingTransactions();
    List<BorrowedItem> getAllTransactionHistory();
    List<ItemUsage> getMostUsedEquipment();

    boolean approveBorrowRequest(int transactionId, int equipmentId, int adminUserId);
    String approveBorrow(int transactionId, int equipmentId, int adminUserId);

    List<ItemFee> getItemFees();
    String updateItemFees(int equipmentId, double borrowFee, double lateFeePerDay);
    boolean rejectBorrowRequest(int transactionId, int equipmentId, int adminUserId);

    // --- Password Tickets Workflow ---
    String createPasswordTicket(int userId); // Changed from boolean to String
    List<PasswordTicket> getPendingPasswordTickets();
    boolean updatePasswordTicketStatus(int ticketId, String status, int adminUserId);
}