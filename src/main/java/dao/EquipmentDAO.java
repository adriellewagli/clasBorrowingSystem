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

    /** Record a new borrow transaction */
    boolean createBorrowRequest(int userId, int equipmentId, LocalDate expectedReturnDate);

    /** Fetch active borrow transactions for a specific user */
    List<BorrowedItem> getActiveBorrowsForUser(int userId);

    /** Process an item return request */
    boolean processReturnRequest(int transactionId, int equipmentId);

    /** Fetch all active borrow transactions for the Admin dashboard */
    List<BorrowedItem> getAllActiveTransactions();
}