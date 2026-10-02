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
import java.util.Collections;
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

        String sql = "SELECT e.equipment_id AS id, e.item_name AS name, e.category, e.serial_number, " +
                "CASE " +
                "   WHEN t.status = 'BORROWED' THEN 'Checked Out' " +
                "   WHEN t.status = 'PENDING' THEN 'Pending Approval' " +
                "   WHEN e.status = 'MAINTENANCE' OR e.status = 'REPAIR' THEN 'In Maintenance' " +
                "   ELSE 'Available' " +
                "END AS display_status " +
                "FROM equipment e " +
                "LEFT JOIN transactions t ON e.equipment_id = t.equipment_id AND t.status IN ('PENDING', 'BORROWED')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(new Equipment(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("display_status"),
                        rs.getString("serial_number"),
                        1
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

        String dbStatus;
        if ("Checked Out".equalsIgnoreCase(status)) {
            dbStatus = "BORROWED";
        } else if ("Pending Approval".equalsIgnoreCase(status)) {
            dbStatus = "PENDING";
        } else {
            dbStatus = status.toUpperCase();
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, dbStatus);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String rawStatus = rs.getString("status");
                    String uiStatus = "Available";
                    if ("BORROWED".equalsIgnoreCase(rawStatus)) uiStatus = "Checked Out";
                    else if ("PENDING".equalsIgnoreCase(rawStatus)) uiStatus = "Pending Approval";
                    else if ("MAINTENANCE".equalsIgnoreCase(rawStatus)) uiStatus = "In Maintenance";

                    list.add(new Equipment(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            uiStatus,
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
        String sql = "UPDATE equipment SET status = ? WHERE equipment_id = ?";
        String dbStatus;
        if ("Checked Out".equalsIgnoreCase(newStatus)) {
            dbStatus = "BORROWED";
        } else if ("Pending Approval".equalsIgnoreCase(newStatus)) {
            dbStatus = "PENDING";
        } else {
            dbStatus = newStatus.toUpperCase();
        }

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
    public boolean createBorrowRequest(int equipmentId, String borrowerName, String borrowerType,
                                       String borrowerIdNumber, String programOrDept, LocalDate returnTargetDate,
                                       int requestedByUserId, double totalFeeCharged, byte[] idSnapshotBytes) {
        return createBatchBorrowRequest(
                Collections.singletonList(equipmentId),
                borrowerName,
                borrowerType,
                borrowerIdNumber,
                programOrDept,
                returnTargetDate,
                requestedByUserId,
                totalFeeCharged,
                idSnapshotBytes
        );
    }

    @Override
    public boolean createBatchBorrowRequest(List<Integer> equipmentIds, String borrowerName, String borrowerType,
                                            String borrowerIdNumber, String programOrDept, LocalDate returnTargetDate,
                                            int requestedByUserId, double totalFeeChargedPerItem, byte[] idSnapshotBytes) {

        if (equipmentIds == null || equipmentIds.isEmpty()) return false;

        // Set initial processed_by to validUserId so MySQL won't fail if column is NOT NULL
        String insertSql = "INSERT INTO transactions " +
                "(equipment_id, requested_by, borrower_name, date_borrowed, expected_return_date, status, processed_by, " +
                "borrower_type, borrower_id_number, program_or_dept, initial_condition, daily_fee, " +
                "late_penalty_per_day, total_fee_charged, id_snapshot) " +
                "VALUES (?, ?, ?, CURDATE(), ?, 'PENDING', ?, ?, ?, ?, 'Good', 20.00, 50.00, ?, ?)";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            int validUserId = (requestedByUserId > 0) ? requestedByUserId : 1;

            try (PreparedStatement stmtInsert = conn.prepareStatement(insertSql)) {
                for (int eqId : equipmentIds) {
                    stmtInsert.setInt(1, eqId);
                    stmtInsert.setInt(2, validUserId); // requested_by
                    stmtInsert.setString(3, borrowerName);
                    stmtInsert.setDate(4, java.sql.Date.valueOf(returnTargetDate));
                    stmtInsert.setInt(5, validUserId); // processed_by (initial default to avoid NOT NULL violation)
                    stmtInsert.setString(6, borrowerType);
                    stmtInsert.setString(7, borrowerIdNumber);
                    stmtInsert.setString(8, programOrDept);
                    stmtInsert.setDouble(9, totalFeeChargedPerItem);

                    if (idSnapshotBytes != null && idSnapshotBytes.length > 0) {
                        stmtInsert.setBytes(10, idSnapshotBytes);
                    } else {
                        stmtInsert.setNull(10, java.sql.Types.BLOB);
                    }
                    stmtInsert.addBatch();
                }

                stmtInsert.executeBatch();
                conn.commit();
                System.out.println("✅ [EquipmentDAOImpl Success] Logged PENDING transactions for requested_by: " + validUserId);
                return true;
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException rollbackEx) { rollbackEx.printStackTrace(); }
            }
            System.err.println("❌ [EquipmentDAOImpl Error] SQL Exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public List<BorrowedItem> getActiveBorrowsForUser(int userId) {
        List<BorrowedItem> list = new ArrayList<>();

        // Filters strictly on t.requested_by so items stay visible regardless of who approved them
        String sql = "SELECT t.transaction_id, e.equipment_id, e.item_name, e.category, e.serial_number, " +
                "t.borrower_name, t.date_borrowed, t.expected_return_date, t.status " +
                "FROM transactions t " +
                "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                "WHERE t.requested_by = ? " +
                "AND UPPER(t.status) IN ('BORROWED', 'ACTIVE', 'APPROVED', 'PENDING RETURN')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate expectedReturn = rs.getDate("expected_return_date") != null
                            ? rs.getDate("expected_return_date").toLocalDate()
                            : LocalDate.now();

                    String rawStatus = rs.getString("status");
                    String uiStatus = "Active";
                    if ("BORROWED".equalsIgnoreCase(rawStatus)) {
                        uiStatus = expectedReturn.isBefore(LocalDate.now()) ? "Overdue" : "Active";
                    }

                    BorrowedItem item = new BorrowedItem(
                            rs.getInt("transaction_id"),
                            rs.getInt("equipment_id"),
                            rs.getString("item_name"),
                            rs.getString("category"),
                            rs.getString("serial_number"),
                            rs.getDate("date_borrowed") != null ? rs.getDate("date_borrowed").toLocalDate() : LocalDate.now(),
                            expectedReturn,
                            uiStatus
                    );
                    item.setBorrowerName(rs.getString("borrower_name"));
                    list.add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to load active borrows for requested_by user " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<BorrowedItem> getBorrowHistoryForUser(int userId) {
        List<BorrowedItem> list = new ArrayList<>();
        String sql = "SELECT t.transaction_id, e.item_name, e.category, e.serial_number, t.borrower_name, " +
                "t.date_borrowed, t.expected_return_date, t.status, u.username AS processor_username " +
                "FROM transactions t " +
                "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                "LEFT JOIN users u ON t.processed_by = u.user_id " +
                "WHERE t.requested_by = ? " +
                "ORDER BY t.transaction_id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate expectedReturn = rs.getDate("expected_return_date") != null
                            ? rs.getDate("expected_return_date").toLocalDate()
                            : LocalDate.now();

                    String rawStatus = rs.getString("status");
                    String uiStatus = "Pending";

                    if ("BORROWED".equalsIgnoreCase(rawStatus)) {
                        uiStatus = expectedReturn.isBefore(LocalDate.now()) ? "Overdue" : "Approved";
                    } else if ("RETURNED".equalsIgnoreCase(rawStatus)) {
                        uiStatus = "Returned";
                    } else if ("REJECTED".equalsIgnoreCase(rawStatus)) {
                        uiStatus = "Rejected";
                    }

                    BorrowedItem item = new BorrowedItem(
                            rs.getInt("transaction_id"),
                            rs.getString("item_name"),
                            rs.getString("category"),
                            rs.getString("serial_number"),
                            rs.getDate("date_borrowed") != null ? rs.getDate("date_borrowed").toLocalDate() : LocalDate.now(),
                            expectedReturn,
                            uiStatus
                    );
                    item.setBorrowerName(rs.getString("borrower_name"));
                    item.setProcessedBy(rs.getString("processor_username"));
                    list.add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch borrow history: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<BorrowedItem> getAllActiveTransactions() {
        List<BorrowedItem> list = new ArrayList<>();
        String sql = "SELECT t.transaction_id, e.equipment_id, e.item_name, e.category, e.serial_number, " +
                "t.borrower_name, t.date_borrowed, t.expected_return_date, t.status, " +
                "u_req.username AS requester_username, " +
                "u_proc.username AS processor_username " +
                "FROM transactions t " +
                "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                "LEFT JOIN users u_req ON t.requested_by = u_req.user_id " +
                "LEFT JOIN users u_proc ON t.processed_by = u_proc.user_id " +
                "WHERE UPPER(t.status) IN ('BORROWED', 'ACTIVE', 'APPROVED', 'PENDING RETURN')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                LocalDate expectedReturn = rs.getDate("expected_return_date") != null
                        ? rs.getDate("expected_return_date").toLocalDate()
                        : LocalDate.now();
                String uiStatus = expectedReturn.isBefore(LocalDate.now()) ? "Overdue" : "Active";

                BorrowedItem item = new BorrowedItem(
                        rs.getInt("transaction_id"),
                        rs.getInt("equipment_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getString("serial_number"),
                        rs.getDate("date_borrowed") != null ? rs.getDate("date_borrowed").toLocalDate() : LocalDate.now(),
                        expectedReturn,
                        uiStatus
                );
                item.setBorrowerName(rs.getString("borrower_name"));

                // Set who requested the borrowing
                String reqUser = rs.getString("requester_username");
                item.setRequestedBy(reqUser != null ? reqUser : "N/A");

                // Set who approved/processed it
                String procUser = rs.getString("processor_username");
                item.setProcessedBy(procUser != null ? procUser : "Pending");

                list.add(item);
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch all active transactions: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<BorrowedItem> getPendingTransactions() {
        List<BorrowedItem> list = new ArrayList<>();
        String sql = "SELECT t.transaction_id, e.equipment_id, e.item_name, e.serial_number, e.category, " +
                "t.borrower_name, t.borrower_type, t.borrower_id_number, t.program_or_dept, " +
                "t.date_borrowed, t.expected_return_date, t.total_fee_charged, t.id_snapshot, u.username AS processor_username " +
                "FROM transactions t " +
                "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                "LEFT JOIN users u ON t.processed_by = u.user_id " +
                "WHERE t.status = 'PENDING' " +
                "ORDER BY t.transaction_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                BorrowedItem item = new BorrowedItem(
                        rs.getInt("transaction_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getString("serial_number"),
                        rs.getDate("date_borrowed").toLocalDate(),
                        rs.getDate("expected_return_date").toLocalDate(),
                        "Pending"
                );
                item.setEquipmentId(rs.getInt("equipment_id"));
                item.setBorrowerName(rs.getString("borrower_name"));
                item.setProcessedBy(rs.getString("processor_username"));
                list.add(item);
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch pending transactions: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<BorrowedItem> getAllTransactionHistory() {
        List<BorrowedItem> list = new ArrayList<>();
        String sql = "SELECT t.transaction_id, e.equipment_id, e.item_name, e.category, e.serial_number, " +
                "t.borrower_name, t.date_borrowed, t.expected_return_date, t.status, u.username AS processor_username " +
                "FROM transactions t " +
                "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                "LEFT JOIN users u ON t.processed_by = u.user_id " +
                "ORDER BY t.transaction_id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                LocalDate expectedReturn = rs.getDate("expected_return_date") != null
                        ? rs.getDate("expected_return_date").toLocalDate()
                        : LocalDate.now();

                String rawStatus = rs.getString("status");
                String uiStatus = "Pending";
                if ("BORROWED".equalsIgnoreCase(rawStatus)) {
                    uiStatus = expectedReturn.isBefore(LocalDate.now()) ? "Overdue" : "Active";
                } else if ("RETURNED".equalsIgnoreCase(rawStatus)) {
                    uiStatus = "Returned";
                } else if ("REJECTED".equalsIgnoreCase(rawStatus)) {
                    uiStatus = "Rejected";
                }

                BorrowedItem item = new BorrowedItem(
                        rs.getInt("transaction_id"),
                        rs.getInt("equipment_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getString("serial_number"),
                        rs.getDate("date_borrowed") != null ? rs.getDate("date_borrowed").toLocalDate() : LocalDate.now(),
                        expectedReturn,
                        uiStatus
                );
                item.setBorrowerName(rs.getString("borrower_name"));
                item.setProcessedBy(rs.getString("processor_username"));
                list.add(item);
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch full audit log: " + e.getMessage());
        }
        return list;
    }

    @Override
    public boolean approveBorrowRequest(int transactionId, int equipmentId, int adminUserId) {
        String updateTxSql = "UPDATE transactions SET status = 'BORROWED', processed_by = ? WHERE transaction_id = ?";
        String updateEqSql = "UPDATE equipment SET status = 'BORROWED' WHERE equipment_id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmtTx = conn.prepareStatement(updateTxSql);
                 PreparedStatement stmtEq = conn.prepareStatement(updateEqSql)) {

                stmtTx.setInt(1, adminUserId);
                stmtTx.setInt(2, transactionId);
                stmtTx.executeUpdate();

                stmtEq.setInt(1, equipmentId);
                stmtEq.executeUpdate();

                conn.commit();
                System.out.println("✅ [EquipmentDAOImpl Success] Approved tx ID: " + transactionId + " by Admin ID: " + adminUserId);
                return true;
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException rollbackEx) { rollbackEx.printStackTrace(); }
            }
            System.err.println("❌ [EquipmentDAOImpl Error] Failed to approve borrow request: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }
    }

    @Override
    public boolean rejectBorrowRequest(int transactionId, int equipmentId, int adminUserId) {
        String updateTxSql = "UPDATE transactions SET status = 'REJECTED', processed_by = ? WHERE transaction_id = ?";
        String updateEqSql = "UPDATE equipment SET status = 'AVAILABLE' WHERE equipment_id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmtTx = conn.prepareStatement(updateTxSql);
                 PreparedStatement stmtEq = conn.prepareStatement(updateEqSql)) {

                stmtTx.setInt(1, adminUserId);
                stmtTx.setInt(2, transactionId);
                stmtTx.executeUpdate();

                stmtEq.setInt(1, equipmentId);
                stmtEq.executeUpdate();

                conn.commit();
                System.out.println("✅ [EquipmentDAOImpl Success] Rejected tx ID: " + transactionId + " by Admin ID: " + adminUserId);
                return true;
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException rollbackEx) { rollbackEx.printStackTrace(); }
            }
            System.err.println("❌ [EquipmentDAOImpl Error] Failed to reject borrow request: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }
    }

    @Override
    public boolean processReturnRequest(int transactionId, int ignoredEquipmentId, int adminUserId) {
        String getEqIdSql = "SELECT equipment_id FROM transactions WHERE transaction_id = ?";
        String updateTxSql = "UPDATE transactions SET status = 'RETURNED', actual_return_date = CURRENT_DATE, processed_by = ? WHERE transaction_id = ?";
        String updateEqSql = "UPDATE equipment SET status = 'AVAILABLE' WHERE equipment_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmtGet = conn.prepareStatement(getEqIdSql);
                 PreparedStatement stmtTx = conn.prepareStatement(updateTxSql);
                 PreparedStatement stmtEq = conn.prepareStatement(updateEqSql)) {

                stmtGet.setInt(1, transactionId);
                ResultSet rs = stmtGet.executeQuery();

                if (!rs.next()) {
                    System.err.println("[EquipmentDAOImpl Error] Transaction ID not found.");
                    conn.rollback();
                    return false;
                }
                int actualEquipmentId = rs.getInt("equipment_id");

                stmtTx.setInt(1, adminUserId);
                stmtTx.setInt(2, transactionId);
                stmtTx.executeUpdate();

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