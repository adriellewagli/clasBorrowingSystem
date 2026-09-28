package dao;

import db.DatabaseConnection;
import model.BorrowedItem;
import model.Equipment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class EquipmentDAOImpl implements EquipmentDAO {

    @Override
    public boolean addEquipment(Equipment equipment) {
        String sql = "INSERT INTO equipment (item_name, category, serial_number, status) VALUES (?, ?, ?, 'AVAILABLE')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, equipment.getName());
            stmt.setString(2, equipment.getCategory());
            stmt.setString(3, equipment.getSerialNumber());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to add equipment: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<Equipment> getAllEquipment() {
        List<Equipment> list = new ArrayList<>();
        // FIX: Used SQL aliases (AS id, AS name) to match your DB to your Java code.
        // We pass '1' for quantity since your DB doesn't have a quantity column.
        String sql = "SELECT equipment_id AS id, item_name AS name, category, status, serial_number, 1 AS quantity FROM equipment";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(new Equipment(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        // Convert DB "BORROWED" back to UI "Checked Out"
                        rs.getString("status").equalsIgnoreCase("BORROWED") ? "Checked Out" : rs.getString("status"),
                        rs.getString("serial_number"),
                        rs.getInt("quantity")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch equipment: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<Equipment> getEquipmentByStatus(String status) {
        List<Equipment> list = new ArrayList<>();
        String sql = "SELECT equipment_id AS id, item_name AS name, category, status, serial_number, 1 AS quantity FROM equipment WHERE status = ?";

        // Convert UI status "Checked Out" to DB ENUM "BORROWED"
        String dbStatus = status.equalsIgnoreCase("Checked Out") ? "BORROWED" : status.toUpperCase();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, dbStatus);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Equipment(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getString("status").equalsIgnoreCase("BORROWED") ? "Checked Out" : rs.getString("status"),
                            rs.getString("serial_number"),
                            rs.getInt("quantity")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch equipment by status: " + e.getMessage());
        }
        return list;
    }

    @Override
    public boolean updateStatus(int equipmentId, String newStatus) {
        // FIX: Match DB column 'equipment_id'
        String sql = "UPDATE equipment SET status = ? WHERE equipment_id = ?";

        // Convert UI status "Checked Out" to DB ENUM "BORROWED"
        String dbStatus = newStatus.equalsIgnoreCase("Checked Out") ? "BORROWED" : newStatus.toUpperCase();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, dbStatus);
            stmt.setInt(2, equipmentId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to update equipment status: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createBorrowRequest(int userId, int equipmentId, LocalDate expectedReturnDate) {
        // FIX 1: Insert into 'transactions' table
        // FIX 2: Matched exact transactions table columns
        String insertSql = "INSERT INTO transactions (equipment_id, borrower_name, date_borrowed, expected_return_date, status, processed_by) " +
                "VALUES (?, 'Current User', CURRENT_DATE, ?, 'BORROWED', ?)";

        // FIX 3: Update target is equipment_id, setting status to DB Enum 'BORROWED'
        String updateEqSql = "UPDATE equipment SET status = 'BORROWED' WHERE equipment_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // Enable transaction handling

            try (PreparedStatement stmtInsert = conn.prepareStatement(insertSql);
                 PreparedStatement stmtUpdate = conn.prepareStatement(updateEqSql)) {

                // 1. Insert Transaction record
                stmtInsert.setInt(1, equipmentId);
                stmtInsert.setObject(2, expectedReturnDate);
                stmtInsert.setInt(3, userId); // Maps to 'processed_by'
                stmtInsert.executeUpdate();

                // 2. Update Equipment status
                stmtUpdate.setInt(1, equipmentId);
                stmtUpdate.executeUpdate();

                // Commit both changes
                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[EquipmentDAOImpl Error] Transaction failed, rolled back: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Database connection failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<BorrowedItem> getActiveBorrowsForUser(int userId) {
        List<BorrowedItem> list = new ArrayList<>();
        // Join transactions and equipment tables to get all data needed for BorrowedItem model
        String sql = "SELECT t.transaction_id, e.item_name, e.category, e.serial_number, " +
                "t.date_borrowed, t.expected_return_date, t.status " +
                "FROM transactions t " +
                "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                "WHERE t.processed_by = ? AND t.status = 'BORROWED'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {

                    LocalDate expectedReturn = rs.getDate("expected_return_date").toLocalDate();

                    // Determine UI Status based on dates
                    String uiStatus = "Active";
                    if (expectedReturn.isBefore(LocalDate.now())) {
                        uiStatus = "Overdue";
                    }

                    list.add(new BorrowedItem(
                            rs.getInt("transaction_id"), // Maps to borrowId
                            rs.getString("item_name"),
                            rs.getString("category"),
                            rs.getString("serial_number"),
                            rs.getDate("date_borrowed").toLocalDate(),
                            expectedReturn,
                            uiStatus
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch active borrows: " + e.getMessage());
        }
        return list;
    }


    @Override
    public List<BorrowedItem> getAllActiveTransactions() {
        List<BorrowedItem> list = new ArrayList<>();
        // Note: We use borrower_name for Admin view
        String sql = "SELECT t.transaction_id, e.equipment_id, e.item_name, e.serial_number, " +
                "t.borrower_name, t.date_borrowed, t.expected_return_date, t.status " +
                "FROM transactions t " +
                "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                "WHERE t.status = 'BORROWED'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                LocalDate expectedReturn = rs.getDate("expected_return_date").toLocalDate();
                String uiStatus = expectedReturn.isBefore(LocalDate.now()) ? "Overdue" : "Active";

                BorrowedItem item = new BorrowedItem(
                        rs.getInt("transaction_id"),
                        rs.getString("item_name"),
                        rs.getString("borrower_name"), // Reusing category field to hold borrower name temporarily for the table
                        rs.getString("serial_number"),
                        rs.getDate("date_borrowed").toLocalDate(),
                        expectedReturn,
                        uiStatus
                );
                // Quick hack: Storing equipment_id inside a custom field or simply adding a getter in BorrowedItem is ideal.
                // For now, we will map borrower_name to the 'category' field in your BorrowedItem model so we don't have to break it.
                list.add(item);
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch all active transactions: " + e.getMessage());
        }
        return list;
    }

    @Override
    public boolean processReturnRequest(int transactionId, int ignoredEquipmentId) {
        // 1. First, find out which equipment is tied to this transaction
        String getEqIdSql = "SELECT equipment_id FROM transactions WHERE transaction_id = ?";

        // 2. Mark the transaction as returned
        String updateTxSql = "UPDATE transactions SET status = 'RETURNED', actual_return_date = CURRENT_DATE WHERE transaction_id = ?";

        // 3. Mark the equipment as available
        String updateEqSql = "UPDATE equipment SET status = 'AVAILABLE' WHERE equipment_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // Enable transaction block

            try (PreparedStatement stmtGet = conn.prepareStatement(getEqIdSql);
                 PreparedStatement stmtTx = conn.prepareStatement(updateTxSql);
                 PreparedStatement stmtEq = conn.prepareStatement(updateEqSql)) {

                // Step 1: Find the equipment_id
                stmtGet.setInt(1, transactionId);
                ResultSet rs = stmtGet.executeQuery();

                if (!rs.next()) {
                    System.err.println("[EquipmentDAOImpl Error] Transaction ID not found.");
                    conn.rollback();
                    return false;
                }
                int actualEquipmentId = rs.getInt("equipment_id");

                // Step 2: Update the transaction
                stmtTx.setInt(1, transactionId);
                stmtTx.executeUpdate();

                // Step 3: Update the equipment
                stmtEq.setInt(1, actualEquipmentId);
                stmtEq.executeUpdate();

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[EquipmentDAOImpl Error] Return transaction failed, rolled back: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Database connection failed: " + e.getMessage());
            return false;
        }
    }
}