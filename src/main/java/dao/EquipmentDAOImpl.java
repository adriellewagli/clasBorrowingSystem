package dao;

import db.DatabaseConnection;
import db.DbHealth;
import model.BorrowedItem;
import model.DamageReport;
import model.Equipment;
import model.ItemFee;
import model.ItemUsage;
import model.PasswordTicket;
import model.Receipt;
import model.BorrowerDetails;
import model.UserSession;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EquipmentDAOImpl implements EquipmentDAO {

    /** Upper limit the Super Admin can set for a fee (keeps a typing slip from saving a silly amount). */
    private static final double MAX_FEE = 100_000;

    /** Which screen the rows are for - decides the wording of the status label. */
    private enum Mode { AUDIT, USER_ACTIVE, USER_HISTORY, ADMIN_ACTIVE, RETURN_QUEUE, PENDING }

    /** Columns every transaction list needs (the SQL aliases are what mapRow reads). */
    private static final String TX_COLUMNS =
            "t.transaction_id, e.equipment_id, e.item_name, e.category, e.serial_number, " +
                    "t.borrower_name, t.borrower_type, t.borrower_id_number, " +
                    "t.date_borrowed, t.expected_return_date, t.actual_return_date, t.return_requested_on, " +
                    "t.status AS tx_status, t.total_fee_charged, t.late_penalty_per_day, t.late_fee_charged, t.approved_at, " +
                    "e.borrow_fee AS item_borrow_fee, e.late_fee_per_day AS item_late_fee, " +
                    "u_req.username AS requester_username, u_proc.username AS processor_username ";

    private static final String TX_FROM =
            "FROM transactions t " +
                    "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                    "LEFT JOIN users u_req ON t.requested_by = u_req.user_id " +
                    "LEFT JOIN users u_proc ON t.processed_by = u_proc.user_id ";
    private static Double borrowFeeOf(Connection conn, int equipmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT borrow_fee FROM equipment WHERE equipment_id = ?")) {
            ps.setInt(1, equipmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }
    // ================================================================== equipment

    @Override
    public int addEquipmentGetId(Equipment equipment) {
        String sql = "INSERT INTO equipment (item_name, category, serial_number, status) VALUES (?, ?, ?, 'AVAILABLE')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, equipment.getName());
            stmt.setString(2, equipment.getCategory());
            stmt.setString(3, equipment.getSerialNumber());
            if (stmt.executeUpdate() > 0) {
                try (ResultSet k = stmt.getGeneratedKeys()) {
                    if (k.next()) return k.getInt(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to add equipment: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public String removeEquipment(int equipmentId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM transactions WHERE equipment_id = ?")) {
                ps.setInt(1, equipmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return "This item has transactions on record (active or past), so it can't be removed. Disable it instead.";
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM equipment WHERE equipment_id = ? " +
                            "AND NOT EXISTS (SELECT 1 FROM transactions WHERE equipment_id = ?)")) {
                ps.setInt(1, equipmentId);
                ps.setInt(2, equipmentId);
                return ps.executeUpdate() > 0 ? null : "That item no longer exists or just got a transaction. Refresh the list.";
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to remove equipment: " + e.getMessage());
            return "Database error: could not remove the item.";
        }
    }

    @Override
    public String setEquipmentDisabled(int equipmentId, boolean disabled) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE equipment SET is_disabled = ? WHERE equipment_id = ?")) {
            ps.setBoolean(1, disabled);
            ps.setInt(2, equipmentId);
            return ps.executeUpdate() > 0 ? null : "That item no longer exists. Refresh the list.";
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to update disabled flag: " + e.getMessage());
            return "Database error: could not update the item.";
        }
    }

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
                "e.borrow_fee, e.late_fee_per_day, " +
                "CASE " +
                "   WHEN EXISTS (SELECT 1 FROM transactions t WHERE t.equipment_id = e.equipment_id " +
                "                AND UPPER(t.status) IN ('BORROWED', 'ACTIVE', 'APPROVED', 'PENDING RETURN')) THEN 'Checked Out' " +
                "   WHEN EXISTS (SELECT 1 FROM transactions t WHERE t.equipment_id = e.equipment_id " +
                "                AND UPPER(t.status) = 'PENDING') THEN 'Pending Approval' " +
                "   WHEN UPPER(e.status) IN ('MAINTENANCE', 'REPAIR', 'IN MAINTENANCE') THEN 'In Maintenance' " +
                "   ELSE 'Available' " +
                "END AS display_status " +
                "FROM equipment e ORDER BY e.equipment_id";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Equipment eq = new Equipment(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("display_status"),
                        rs.getString("serial_number"),
                        1
                );
                eq.setBorrowFee(rs.getDouble("borrow_fee"));
                eq.setLateFeePerDay(rs.getDouble("late_fee_per_day"));
                list.add(eq);
            }
        } catch (SQLException e) {
            DbHealth.readFailed();
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch equipment: " + e.getMessage());
        }
        return list;
    }

    @Override
    public List<Equipment> getEquipmentByStatus(String status) {
        List<Equipment> list = new ArrayList<>();

        String dbStatus = toDbStatus(status);
        String where = "MAINTENANCE".equals(dbStatus) ? "status IN (?, 'IN MAINTENANCE')" : "status = ?";
        String sql = "SELECT equipment_id AS id, item_name AS name, category, status, serial_number, 1 AS quantity " +
                "FROM equipment WHERE " + where;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, dbStatus);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String rawStatus = rs.getString("status");
                    String uiStatus = "Available";
                    if ("BORROWED".equalsIgnoreCase(rawStatus)) uiStatus = "Checked Out";
                    else if ("PENDING".equalsIgnoreCase(rawStatus)) uiStatus = "Pending Approval";
                    else if ("MAINTENANCE".equalsIgnoreCase(rawStatus) || "IN MAINTENANCE".equalsIgnoreCase(rawStatus)) uiStatus = "In Maintenance";
                    else if ("REPAIR".equalsIgnoreCase(rawStatus)) uiStatus = "In Repair";

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
            DbHealth.readFailed();
            System.err.println("[EquipmentDAOImpl Error] Failed to fetch equipment by status: " + e.getMessage());
        }
        return list;
    }

    private static String toDbStatus(String uiStatus) {
        if ("Checked Out".equalsIgnoreCase(uiStatus)) return "BORROWED";
        if ("Pending Approval".equalsIgnoreCase(uiStatus)) return "PENDING";
        if ("In Maintenance".equalsIgnoreCase(uiStatus)) return "MAINTENANCE";
        return uiStatus.toUpperCase();
    }

    @Override
    public boolean updateStatus(int equipmentId, String newStatus) {
        String sql = "UPDATE equipment SET status = ? WHERE equipment_id = ?";
        String dbStatus = toDbStatus(newStatus);

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

    // ================================================================== creating requests

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
        if (equipmentIds == null || equipmentIds.isEmpty() || returnTargetDate == null) return false;
        Map<Integer, LocalDate> dates = new java.util.HashMap<>();
        for (int id : equipmentIds) dates.put(id, returnTargetDate);
        return createBatchBorrowRequest(equipmentIds, dates, borrowerName, borrowerType, borrowerIdNumber,
                programOrDept, requestedByUserId, idSnapshotBytes);
    }

    @Override
    public boolean createBatchBorrowRequest(List<Integer> equipmentIds, Map<Integer, LocalDate> returnDates,
                                            String borrowerName, String borrowerType, String borrowerIdNumber,
                                            String programOrDept, int requestedByUserId, byte[] idSnapshotBytes) {
        return submitBatchBorrowRequest(equipmentIds, returnDates, borrowerName, borrowerType, borrowerIdNumber,
                programOrDept, requestedByUserId, idSnapshotBytes) == null;
    }

    @Override
    public String submitBatchBorrowRequest(List<Integer> equipmentIds, Map<Integer, LocalDate> returnDates,
                                           String borrowerName, String borrowerType, String borrowerIdNumber,
                                           String programOrDept, int requestedByUserId, byte[] idSnapshotBytes) {
        // old callers have no prices: every item is recorded at 0.00
        return submitBatchBorrowRequest(equipmentIds, returnDates, new java.util.HashMap<>(), borrowerName,
                borrowerType, borrowerIdNumber, programOrDept, requestedByUserId, idSnapshotBytes);
    }

    @Override
    public String submitBatchBorrowRequest(List<Integer> equipmentIds, Map<Integer, LocalDate> returnDates,
                                           Map<Integer, Double> prices,
                                           String borrowerName, String borrowerType, String borrowerIdNumber,
                                           String programOrDept, int requestedByUserId, byte[] idSnapshotBytes) {

        if (equipmentIds == null || equipmentIds.isEmpty() || returnDates == null) return "Nothing to submit.";

        java.util.List<Integer> ids = new ArrayList<>(new java.util.LinkedHashSet<>(equipmentIds));

        String insertSql = "INSERT INTO transactions " +
                "(equipment_id, requested_by, borrower_name, date_borrowed, expected_return_date, status, processed_by, " +
                "borrower_type, borrower_id_number, program_or_dept, initial_condition, daily_fee, " +
                "late_penalty_per_day, total_fee_charged, id_snapshot) " +
                "VALUES (?, ?, ?, CURDATE(), ?, 'PENDING', ?, ?, ?, ?, 'Good', 0.00, ?, ?, ?)";

        LocalDate today = LocalDate.now();

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            int validUserId = (requestedByUserId > 0) ? requestedByUserId : 1;

            for (int eqId : ids) {
                String name;
                String eqStatus;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT item_name, status FROM equipment WHERE equipment_id = ? FOR UPDATE")) {
                    ps.setInt(1, eqId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return "An item in your queue no longer exists."; }
                        name = rs.getString("item_name");
                        eqStatus = upper(rs.getString("status"));
                    }
                }
                if (eqStatus.equals("MAINTENANCE") || eqStatus.equals("IN MAINTENANCE") || eqStatus.equals("REPAIR")) {
                    conn.rollback();
                    return "'" + name + "' is under maintenance and can't be requested.";
                }

                LocalDate due = returnDates.get(eqId);
                if (due == null || due.isBefore(today.plusDays(1))) {
                    conn.rollback();
                    return "Choose a return date (tomorrow or later) for '" + name + "'.";
                }

                Double price = prices == null ? null : prices.get(eqId);
                double fee = price == null ? 0.0 : price;
                if (Double.isNaN(fee) || fee < 0) { conn.rollback(); return "The price for '" + name + "' can't be negative."; }
                if (fee > MAX_FEE) {
                    conn.rollback();
                    return "The price for '" + name + "' is too high. The most you can enter is " + String.format("%,.0f", MAX_FEE) + ".";
                }

                // the item is needed from today until the return date: nobody else may hold it in that window
                LocalDate[] clash = firstOverlap(bookedWindows(conn, eqId), today, due);
                if (clash != null) {
                    conn.rollback();
                    if (!clash[0].isAfter(today)) {
                        return "'" + name + "' already has a pending request or is checked out. "
                                + "Remove it from your queue (or use Reserve) and try again.";
                    }
                    return "'" + name + "' is reserved from " + clash[0] + " to " + clash[1]
                            + ". Choose a return date before " + clash[0] + ", or reserve it for a later date.";
                }
            }

            try (PreparedStatement stmtInsert = conn.prepareStatement(insertSql)) {
                for (int eqId : ids) {
                    LocalDate due = returnDates.get(eqId);
                    Double price = prices == null ? null : prices.get(eqId);
                    stmtInsert.setInt(1, eqId);
                    stmtInsert.setInt(2, validUserId);
                    stmtInsert.setString(3, borrowerName);
                    stmtInsert.setDate(4, java.sql.Date.valueOf(due));
                    stmtInsert.setInt(5, validUserId);
                    stmtInsert.setString(6, borrowerType);
                    stmtInsert.setString(7, borrowerIdNumber);
                    stmtInsert.setString(8, programOrDept);
                    stmtInsert.setDouble(9, LATE_PENALTY_PER_DAY);
                    stmtInsert.setDouble(10, borrowFeeOf(conn, eqId));

                    if (idSnapshotBytes != null && idSnapshotBytes.length > 0) {
                        stmtInsert.setBytes(11, idSnapshotBytes);
                    } else {
                        stmtInsert.setNull(11, java.sql.Types.BLOB);
                    }
                    stmtInsert.addBatch();
                }

                stmtInsert.executeBatch();
                conn.commit();
                return null;
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException rollbackEx) { rollbackEx.printStackTrace(); }
            }
            System.err.println("[EquipmentDAOImpl Error] Failed to create borrow requests: " + e.getMessage());
            return "Database error: Could not process request.";
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

    // ================================================================== reservations

    /**
     * Every period during which an item is spoken for: [start, end] pairs, oldest first.
     * A request or loan that is still out counts until it is returned (an overdue one blocks up to today);
     * a reservation counts for exactly its pick-up .. return dates.
     */
    private static List<LocalDate[]> bookedWindows(Connection conn, int equipmentId) throws SQLException {
        List<LocalDate[]> windows = new ArrayList<>();
        String sql = "SELECT UPPER(status) AS st, date_borrowed, expected_return_date FROM transactions " +
                "WHERE equipment_id = ? AND UPPER(status) IN " +
                "('PENDING', 'RESERVED', 'BORROWED', 'ACTIVE', 'APPROVED', 'PENDING RETURN') " +
                "ORDER BY date_borrowed";
        LocalDate today = LocalDate.now();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate start = rs.getDate("date_borrowed") == null ? today : rs.getDate("date_borrowed").toLocalDate();
                    LocalDate end = rs.getDate("expected_return_date") == null ? start : rs.getDate("expected_return_date").toLocalDate();
                    if (!"RESERVED".equals(rs.getString("st")) && end.isBefore(today)) end = today; // still out, overdue
                    if (end.isBefore(start)) end = start;
                    windows.add(new LocalDate[]{start, end});
                }
            }
        }
        return windows;
    }

    /** The first booked window that overlaps from..to (both days included), or null if the dates are free. */
    private static LocalDate[] firstOverlap(List<LocalDate[]> windows, LocalDate from, LocalDate to) {
        for (LocalDate[] w : windows) {
            if (!w[0].isAfter(to) && !w[1].isBefore(from)) return w;
        }
        return null;
    }

    @Override
    public List<LocalDate[]> getBookedRanges(int equipmentId) {
        List<LocalDate[]> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            LocalDate today = LocalDate.now();
            for (LocalDate[] w : bookedWindows(conn, equipmentId)) {
                if (!w[1].isBefore(today)) result.add(w);
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to read booked dates: " + e.getMessage());
        }
        return result;
    }

    @Override
    public LocalDate getEarliestReservationDate(int equipmentId) {
        LocalDate d = LocalDate.now().plusDays(1);
        List<LocalDate[]> windows = getBookedRanges(equipmentId);
        boolean moved = true;
        while (moved) {
            moved = false;
            for (LocalDate[] w : windows) {
                if (!d.isBefore(w[0]) && !d.isAfter(w[1])) {
                    d = w[1].plusDays(1);
                    moved = true;
                }
            }
        }
        return d;
    }

    @Override
    public String createReservation(int equipmentId, String borrowerName, String borrowerType,
                                    String borrowerIdNumber, String programOrDept, LocalDate pickupDate,
                                    LocalDate returnDate, int requestedByUserId, double totalFee, byte[] idSnapshotBytes) {
        if (idSnapshotBytes == null || idSnapshotBytes.length == 0) return "ID Verification required: please take an ID snapshot.";
        if (Double.isNaN(totalFee) || totalFee < 0) return "Enter the item's price (0 or more).";
        if (totalFee > MAX_FEE) return "That price is too high. The most you can enter is " + String.format("%,.0f", MAX_FEE) + ".";
        if (pickupDate == null || returnDate == null) return "Choose both a pick-up date and a return date.";
        if (pickupDate.isBefore(LocalDate.now().plusDays(1))) return "The pick-up date must be tomorrow or later.";
        if (returnDate.isBefore(pickupDate)) return "The return date can't be before the pick-up date.";

        try (Connection conn = DatabaseConnection.getConnection()) {
            double lateRate = LATE_PENALTY_PER_DAY;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, borrow_fee, late_fee_per_day FROM equipment WHERE equipment_id = ?")) {
                ps.setInt(1, equipmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "That item no longer exists.";
                    lateRate = rs.getDouble("late_fee_per_day");
                    if (rs.wasNull() || lateRate < 0) lateRate = LATE_PENALTY_PER_DAY;
                    String st = rs.getString(1) == null ? "" : rs.getString(1).toUpperCase(Locale.ROOT);
                    if (st.equals("MAINTENANCE") || st.equals("IN MAINTENANCE") || st.equals("REPAIR")) {
                        return "This item is under maintenance and can't be reserved right now.";
                    }
                }
            }

            LocalDate[] clash = firstOverlap(bookedWindows(conn, equipmentId), pickupDate, returnDate);
            if (clash != null) {
                return "This item is already booked from " + clash[0] + " to " + clash[1]
                        + ". Choose pick-up and return dates that don't overlap it.";
            }

            int uid = requestedByUserId > 0 ? requestedByUserId : 1;
            String insert = "INSERT INTO transactions " +
                    "(equipment_id, requested_by, borrower_name, date_borrowed, expected_return_date, status, processed_by, " +
                    "borrower_type, borrower_id_number, program_or_dept, initial_condition, daily_fee, " +
                    "late_penalty_per_day, total_fee_charged, id_snapshot) " +
                    "VALUES (?, ?, ?, ?, ?, 'RESERVED', ?, ?, ?, ?, 'Good', 0.00, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setInt(1, equipmentId);
                ps.setInt(2, uid);
                ps.setString(3, borrowerName);
                ps.setDate(4, java.sql.Date.valueOf(pickupDate));
                ps.setDate(5, java.sql.Date.valueOf(returnDate));
                ps.setInt(6, uid);
                ps.setString(7, borrowerType);
                ps.setString(8, borrowerIdNumber);
                ps.setString(9, programOrDept);
                ps.setDouble(10, lateRate);
                ps.setDouble(11, borrowFeeOf(conn, equipmentId));
                ps.setBytes(12, idSnapshotBytes);
                ps.executeUpdate();
            }
            return null;

        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to create reservation: " + e.getMessage());
            return "Could not save the reservation: " + e.getMessage();
        }
    }

    // ================================================================== staff: own requests

    @Override
    public List<BorrowedItem> getPendingRequestsForUser(int userId) {
        String sql = "SELECT " + TX_COLUMNS + TX_FROM +
                "WHERE t.requested_by = ? AND UPPER(t.status) IN ('PENDING', 'RESERVED') " +
                "ORDER BY t.transaction_id DESC";
        return queryItems(sql, Mode.PENDING, userId);
    }

    @Override
    public boolean cancelRequest(int transactionId, int userId) {
        String sql = "UPDATE transactions SET status = 'CANCELLED' " +
                "WHERE transaction_id = ? AND requested_by = ? AND UPPER(status) IN ('PENDING', 'RESERVED')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, transactionId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to cancel request " + transactionId + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public int countPendingRequests() {
        String sql = "SELECT COUNT(*) FROM transactions WHERE UPPER(status) IN ('PENDING', 'RESERVED')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            DbHealth.readFailed();
            System.err.println("[EquipmentDAOImpl Error] Failed to count pending requests: " + e.getMessage());
            return 0;
        }
    }

    // ================================================================== lists

    @Override
    public List<BorrowedItem> getActiveBorrowsForUser(int userId) {
        String sql = "SELECT " + TX_COLUMNS + TX_FROM +
                "WHERE t.requested_by = ? " +
                "AND UPPER(t.status) IN ('BORROWED', 'ACTIVE', 'APPROVED', 'PENDING RETURN') " +
                "ORDER BY t.transaction_id DESC";
        return queryItems(sql, Mode.USER_ACTIVE, userId);
    }

    @Override
    public List<BorrowedItem> getBorrowHistoryForUser(int userId) {
        String sql = "SELECT " + TX_COLUMNS + TX_FROM +
                "WHERE t.requested_by = ? " +
                "ORDER BY t.transaction_id DESC";
        return queryItems(sql, Mode.USER_HISTORY, userId);
    }

    @Override
    public List<BorrowedItem> getAllActiveTransactions() {
        String sql = "SELECT " + TX_COLUMNS + TX_FROM +
                "WHERE UPPER(t.status) IN ('BORROWED', 'ACTIVE', 'APPROVED', 'PENDING RETURN')";
        return queryItems(sql, Mode.ADMIN_ACTIVE, null);
    }

    @Override
    public List<BorrowedItem> getPendingReturnTransactions() {
        String sql = "SELECT " + TX_COLUMNS + TX_FROM +
                "WHERE UPPER(t.status) = 'PENDING RETURN' " +
                "ORDER BY t.transaction_id ASC";
        return queryItems(sql, Mode.RETURN_QUEUE, null);
    }

    @Override
    public List<BorrowedItem> getPendingTransactions() {
        String sql = "SELECT " + TX_COLUMNS + TX_FROM +
                "WHERE UPPER(t.status) IN ('PENDING', 'RESERVED') " +
                "ORDER BY t.transaction_id ASC";
        return queryItems(sql, Mode.PENDING, null);
    }

    @Override
    public List<BorrowedItem> getAllTransactionHistory() {
        String sql = "SELECT " + TX_COLUMNS + TX_FROM +
                "ORDER BY COALESCE(t.approved_at, t.date_borrowed) DESC, t.transaction_id DESC";
        return queryItems(sql, Mode.AUDIT, null);
    }

    private List<BorrowedItem> queryItems(String sql, Mode mode, Integer userId) {
        List<BorrowedItem> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (userId != null) stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs, mode));
            }
        } catch (SQLException e) {
            DbHealth.readFailed();
            System.err.println("[EquipmentDAOImpl Error] Failed to load transactions (" + mode + "): " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    private static BorrowedItem mapRow(ResultSet rs, Mode mode) throws SQLException {
        String raw = upper(rs.getString("tx_status"));
        LocalDate due = date(rs, "expected_return_date");
        if (due == null) due = LocalDate.now();
        LocalDate borrowed = date(rs, "date_borrowed");
        if (borrowed == null) borrowed = LocalDate.now();

        BorrowedItem item = new BorrowedItem(
                rs.getInt("transaction_id"),
                rs.getInt("equipment_id"),
                rs.getString("item_name"),
                rs.getString("category"),
                rs.getString("serial_number"),
                borrowed,
                due,
                uiStatus(raw, due, mode));

        item.setBorrowerName(rs.getString("borrower_name"));
        item.setBorrowerType(rs.getString("borrower_type"));
        item.setBorrowerIdNumber(rs.getString("borrower_id_number"));

        String requester = rs.getString("requester_username");
        item.setRequestedBy(requester);

        boolean waiting = raw.equals("PENDING") || raw.equals("RESERVED");
        item.setProcessedBy(waiting ? null : raw.equals("CANCELLED") ? requester : rs.getString("processor_username"));
        item.setRequestType(raw.equals("RESERVED") ? "Reservation" : "Borrow");

        fillBilling(item, rs, raw, due);
        return item;
    }

    private static String uiStatus(String raw, LocalDate due, Mode mode) {
        boolean late = due != null && due.isBefore(LocalDate.now());
        switch (raw) {
            case "BORROWED":
            case "ACTIVE":
            case "APPROVED":
                return late ? "Overdue" : (mode == Mode.USER_HISTORY ? "Approved" : "Active");
            case "PENDING RETURN":
                if (mode == Mode.ADMIN_ACTIVE) return late ? "Overdue" : "Active";
                if (mode == Mode.RETURN_QUEUE) return late ? "Overdue" : "Pending Return";
                return "Pending Return";
            case "RETURNED":  return "Returned";
            case "REJECTED":  return "Rejected";
            case "CANCELLED": return "Cancelled";
            case "RESERVED":  return "Reserved";
            default:          return "Pending";
        }
    }

    private static void fillBilling(BorrowedItem item, ResultSet rs, String raw, LocalDate due) throws SQLException {
        boolean waiting = raw.equals("PENDING") || raw.equals("RESERVED");

        double rate = rs.getDouble("late_penalty_per_day");
        if (rs.wasNull() || rate < 0) rate = LATE_PENALTY_PER_DAY;
        double fee = rs.getDouble("total_fee_charged");
        if (waiting) {
            // the price was entered with the request and is already in total_fee_charged
            rate = moneyOrDefault(rs, "item_late_fee", rate);
        }

        LocalDate actual = date(rs, "actual_return_date");
        LocalDate requestedOn = date(rs, "return_requested_on");

        long days = 0;
        switch (raw) {
            case "BORROWED":
            case "ACTIVE":
            case "APPROVED":
                days = daysLate(due, LocalDate.now());
                break;
            case "PENDING RETURN":
                days = daysLate(due, requestedOn != null ? requestedOn : LocalDate.now());
                break;
            case "RETURNED":
                days = daysLate(due, actual != null ? actual : LocalDate.now());
                break;
            default:
                break;
        }

        double penalty = days * rate;
        if (raw.equals("RETURNED")) {
            double stored = rs.getDouble("late_fee_charged");
            if (stored > 0) penalty = stored;
        }

        item.setLateDays(days);
        item.setLatePenalty(penalty);
        item.setFee(fee);
        item.setFeePending(false);
        item.setLatePenaltyPerDay(rate);

        Timestamp ts = rs.getTimestamp("approved_at");
        if (ts != null) item.setApprovedAt(ts.toLocalDateTime());
    }

    private static double moneyOrDefault(ResultSet rs, String column, double fallback) {
        try {
            double v = rs.getDouble(column);
            return (rs.wasNull() || v < 0) ? fallback : v;
        } catch (SQLException e) {
            return fallback;
        }
    }

    private static long daysLate(LocalDate due, LocalDate until) {
        if (due == null || until == null) return 0;
        long days = ChronoUnit.DAYS.between(due, until);
        return days > 0 ? days : 0;
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    // ================================================================== most used

    @Override
    public List<ItemUsage> getMostUsedEquipment() {
        return getMostUsedEquipment("ALL");
    }

    private static String periodClause(String period) {
        String p = period == null ? "" : period.toUpperCase(Locale.ROOT);
        return switch (p) {
            case "DAY" -> "   AND DATE(t.date_borrowed) = CURDATE() ";
            case "WEEK" -> "   AND YEARWEEK(t.date_borrowed, 1) = YEARWEEK(CURDATE(), 1) ";
            case "MONTH" -> "   AND YEAR(t.date_borrowed) = YEAR(CURDATE()) AND MONTH(t.date_borrowed) = MONTH(CURDATE()) ";
            case "YEAR" -> "   AND YEAR(t.date_borrowed) = YEAR(CURDATE()) ";
            default -> "";
        };
    }

    @Override
    public List<ItemUsage> getMostUsedEquipment(String period) {
        List<ItemUsage> list = new ArrayList<>();
        String sql = "SELECT e.equipment_id, e.item_name, e.category, e.serial_number, e.status, " +
                "COUNT(t.transaction_id) AS times_borrowed, MAX(t.date_borrowed) AS last_borrowed " +
                "FROM equipment e " +
                "LEFT JOIN transactions t ON t.equipment_id = e.equipment_id " +
                "   AND UPPER(t.status) IN ('BORROWED', 'ACTIVE', 'APPROVED', 'PENDING RETURN', 'RETURNED') " +
                periodClause(period) +
                "GROUP BY e.equipment_id, e.item_name, e.category, e.serial_number, e.status " +
                "ORDER BY times_borrowed DESC, last_borrowed DESC, e.item_name ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int rank = 1;
            while (rs.next()) {
                String raw = upper(rs.getString("status"));
                String shown = raw.equals("BORROWED") ? "Checked Out"
                        : (raw.equals("MAINTENANCE") || raw.equals("IN MAINTENANCE") || raw.equals("REPAIR")) ? "In Maintenance"
                        : raw.equals("PENDING") ? "Pending Approval" : "Available";
                java.sql.Date last = rs.getDate("last_borrowed");
                ItemUsage u = new ItemUsage(
                        rs.getInt("equipment_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getString("serial_number"),
                        rs.getInt("times_borrowed"),
                        last == null ? null : last.toLocalDate(),
                        shown);
                u.setRank(rank++);
                list.add(u);
            }
        } catch (SQLException e) {
            DbHealth.readFailed();
            System.err.println("[EquipmentDAOImpl Error] Failed to load most used items: " + e.getMessage());
        }
        return list;
    }

    // ================================================================== super admin: item fees

    @Override
    public List<ItemFee> getItemFees() {
        List<ItemFee> list = new ArrayList<>();
        String sql = "SELECT equipment_id, item_name, category, serial_number, borrow_fee, late_fee_per_day " +
                "FROM equipment ORDER BY item_name ASC, serial_number ASC, equipment_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(new ItemFee(
                        rs.getInt("equipment_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getString("serial_number"),
                        rs.getDouble("borrow_fee"),
                        rs.getDouble("late_fee_per_day")));
            }
        } catch (SQLException e) {
            DbHealth.readFailed();
            System.err.println("[EquipmentDAOImpl Error] Failed to load item fees: " + e.getMessage());
        }
        return list;
    }

    @Override
    public String updateItemFees(int equipmentId, double borrowFee, double lateFeePerDay) {
        if (Double.isNaN(borrowFee) || Double.isNaN(lateFeePerDay) || borrowFee < 0 || lateFeePerDay < 0) {
            return "Fees can't be negative.";
        }
        if (borrowFee > MAX_FEE || lateFeePerDay > MAX_FEE) {
            return "A fee can't be more than " + (long) MAX_FEE + ".";
        }

        String sql = "UPDATE equipment SET borrow_fee = ?, late_fee_per_day = ? WHERE equipment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, borrowFee);
            stmt.setDouble(2, lateFeePerDay);
            stmt.setInt(3, equipmentId);
            return stmt.executeUpdate() > 0 ? null : "That item no longer exists. Refresh the list.";

        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to update fees for item " + equipmentId + ": " + e.getMessage());
            return "Database error: could not save the fees.";
        }
    }

    // ================================================================== password tickets

    @Override
    public String createPasswordTicket(int userId) {
        // 1. Check if a PENDING ticket already exists for this user
        String checkSql = "SELECT COUNT(*) FROM password_tickets WHERE user_id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, userId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return "You already requested a ticket.";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Database error while checking existing tickets.";
        }

        // 2. Insert new ticket
        String sql = "INSERT INTO password_tickets (user_id, status) VALUES (?, 'PENDING')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            return stmt.executeUpdate() > 0 ? null : "Could not submit password reset ticket.";
        } catch (SQLException e) {
            e.printStackTrace();
            return "Database error: " + e.getMessage();
        }
    }

    @Override
    public List<PasswordTicket> getPendingPasswordTickets() {
        List<PasswordTicket> list = new ArrayList<>();
        String sql = "SELECT t.ticket_id, t.user_id, u.username, u.full_name, t.request_date, t.status " +
                "FROM password_tickets t JOIN users u ON t.user_id = u.user_id " +
                "WHERE t.status = 'PENDING' ORDER BY t.request_date ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new PasswordTicket(
                        rs.getInt("ticket_id"),
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getTimestamp("request_date").toLocalDateTime(),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            DbHealth.readFailed();
            System.err.println("[EquipmentDAOImpl Error] Failed to load pending password tickets: " + e.getMessage());
        }
        return list;
    }

    @Override
    public boolean updatePasswordTicketStatus(int ticketId, String status, int adminUserId) {
        String sql = "UPDATE password_tickets SET status = ? WHERE ticket_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, ticketId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to update password ticket status: " + e.getMessage());
            return false;
        }
    }

    // ================================================================== approve / reject

    @Override
    public boolean approveBorrowRequest(int transactionId, int equipmentId, int adminUserId) {
        return approveBorrow(transactionId, equipmentId, adminUserId) == null;
    }

    @Override
    public String approveBorrow(int transactionId, int equipmentId, int adminUserId) {
        String updateTxSql = "UPDATE transactions SET " +
                "date_borrowed = CASE WHEN UPPER(status) = 'RESERVED' THEN CURDATE() ELSE date_borrowed END, " +
                "status = 'BORROWED', processed_by = ?, total_fee_charged = ?, late_penalty_per_day = ?, approved_at = NOW() " +
                "WHERE transaction_id = ?";
        String updateEqSql = "UPDATE equipment SET status = 'BORROWED' WHERE equipment_id = ?";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            String txStatus;
            LocalDate due;
            LocalDate borrowedDate;
            double enteredFee = 0.0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, date_borrowed, expected_return_date, total_fee_charged FROM transactions WHERE transaction_id = ? FOR UPDATE")) {
                ps.setInt(1, transactionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return "That request no longer exists."; }
                    txStatus = upper(rs.getString("status"));
                    due = rs.getDate("expected_return_date") == null ? null : rs.getDate("expected_return_date").toLocalDate();
                    java.sql.Date dbDate = rs.getDate("date_borrowed");
                    borrowedDate = dbDate != null ? dbDate.toLocalDate() : LocalDate.now();
                    enteredFee = rs.getDouble("total_fee_charged");
                }
            }

            if (!txStatus.equals("PENDING") && !txStatus.equals("RESERVED")) {
                conn.rollback();
                return "This request was already handled. Refresh the list.";
            }

            if (txStatus.equals("RESERVED") && borrowedDate.isAfter(LocalDate.now())) {
                conn.rollback();
                return "This reservation starts on " + borrowedDate + ". It can be approved (handed over) from that day.";
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM transactions WHERE equipment_id = ? AND transaction_id <> ? " +
                            "AND UPPER(status) IN ('BORROWED', 'ACTIVE', 'APPROVED', 'PENDING RETURN')")) {
                ps.setInt(1, equipmentId);
                ps.setInt(2, transactionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.rollback();
                        return "This item is still checked out by someone else. Approve it after it has been returned.";
                    }
                }
            }

            double lateRate = LATE_PENALTY_PER_DAY;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, borrow_fee, late_fee_per_day FROM equipment WHERE equipment_id = ? FOR UPDATE")) {
                ps.setInt(1, equipmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return "That item no longer exists."; }
                    String eq = upper(rs.getString("status"));
                    if (eq.equals("MAINTENANCE") || eq.equals("IN MAINTENANCE") || eq.equals("REPAIR")) {
                        conn.rollback();
                        return "This item is under maintenance, so it can't be handed out.";
                    }
                    lateRate = rs.getDouble("late_fee_per_day");
                    if (rs.wasNull() || lateRate < 0) lateRate = LATE_PENALTY_PER_DAY;
                }
            }

            // the price entered with the request is the price charged
            double totalCalculatedFee = enteredFee;

            try (PreparedStatement stmtTx = conn.prepareStatement(updateTxSql);
                 PreparedStatement stmtEq = conn.prepareStatement(updateEqSql)) {

                stmtTx.setInt(1, adminUserId);
                stmtTx.setDouble(2, totalCalculatedFee);
                stmtTx.setDouble(3, lateRate);
                stmtTx.setInt(4, transactionId);
                stmtTx.executeUpdate();

                stmtEq.setInt(1, equipmentId);
                stmtEq.executeUpdate();

                conn.commit();
                return null;
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException rollbackEx) { rollbackEx.printStackTrace(); }
            }
            return "Database error: " + e.getMessage();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }
    }

    @Override
    public boolean rejectBorrowRequest(int transactionId, int equipmentId, int adminUserId) {
        String updateTxSql = "UPDATE transactions SET status = 'REJECTED', processed_by = ? " +
                "WHERE transaction_id = ? AND UPPER(status) IN ('PENDING', 'RESERVED')";
        String updateEqSql = "UPDATE equipment SET status = 'AVAILABLE' WHERE equipment_id = ? AND UPPER(status) = 'PENDING'";

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement stmtTx = conn.prepareStatement(updateTxSql);
                 PreparedStatement stmtEq = conn.prepareStatement(updateEqSql)) {

                stmtTx.setInt(1, adminUserId);
                stmtTx.setInt(2, transactionId);
                if (stmtTx.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }

                stmtEq.setInt(1, equipmentId);
                stmtEq.executeUpdate();

                conn.commit();
                return true;
            }

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException rollbackEx) { rollbackEx.printStackTrace(); }
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }
    }

    // ================================================================== returns

    @Override
    public boolean requestReturn(int transactionId, int userId) {
        String sql = "UPDATE transactions SET status = 'PENDING RETURN', return_requested_on = CURDATE() " +
                "WHERE transaction_id = ? AND requested_by = ? " +
                "AND UPPER(status) IN ('BORROWED', 'ACTIVE', 'APPROVED')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, transactionId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public boolean processReturnRequest(int transactionId, int adminUserId, boolean sendToMaintenance) {
        return processReturnRequest(transactionId, adminUserId, sendToMaintenance, null, null);
    }

    @Override
    public boolean processReturnRequest(int transactionId, int adminUserId, boolean sendToMaintenance,
                                        String condition, String damageReport) {
        String getEqIdSql = "SELECT equipment_id FROM transactions WHERE transaction_id = ?";
        String updateTxSql = "UPDATE transactions SET status = 'RETURNED', " +
                "late_fee_charged = GREATEST(0, DATEDIFF(COALESCE(return_requested_on, CURDATE()), expected_return_date)) " +
                "                   * COALESCE(late_penalty_per_day, ?), " +
                "actual_return_date = COALESCE(return_requested_on, CURDATE()), " +
                "processed_by = ?, return_condition = ?, damage_report = ? " +
                "WHERE transaction_id = ? AND UPPER(status) IN ('PENDING RETURN', 'BORROWED', 'ACTIVE', 'APPROVED')";

        String updateEqSql = "UPDATE equipment SET status = ? WHERE equipment_id = ?";

        String cond = (condition == null || condition.isBlank()) ? null : condition.trim();
        String report = (damageReport == null || damageReport.isBlank()) ? null : damageReport.trim();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmtGet = conn.prepareStatement(getEqIdSql);
                 PreparedStatement stmtTx = conn.prepareStatement(updateTxSql);
                 PreparedStatement stmtEq = conn.prepareStatement(updateEqSql)) {

                stmtGet.setInt(1, transactionId);
                int equipmentId;
                try (ResultSet rs = stmtGet.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    equipmentId = rs.getInt("equipment_id");
                }

                stmtTx.setDouble(1, LATE_PENALTY_PER_DAY);
                stmtTx.setInt(2, adminUserId);
                stmtTx.setString(3, cond);
                stmtTx.setString(4, report);
                stmtTx.setInt(5, transactionId);
                if (stmtTx.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }

                stmtEq.setString(1, sendToMaintenance ? "MAINTENANCE" : "AVAILABLE");
                stmtEq.setInt(2, equipmentId);
                stmtEq.executeUpdate();

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public DamageReport getLatestDamageReport(int equipmentId) {
        String sql = "SELECT transaction_id, borrower_name, return_condition, damage_report, actual_return_date " +
                "FROM transactions WHERE equipment_id = ? AND damage_report IS NOT NULL AND damage_report <> '' " +
                "ORDER BY transaction_id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, equipmentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                java.sql.Date d = rs.getDate("actual_return_date");
                return new DamageReport(
                        rs.getInt("transaction_id"),
                        rs.getString("borrower_name"),
                        rs.getString("return_condition"),
                        rs.getString("damage_report"),
                        d == null ? null : d.toLocalDate());
            }
        } catch (SQLException e) {
            return null;
        }
    }

    // ================================================================== receipt
    @Override
    public BorrowerDetails getBorrowerDetails(int transactionId) {
        // Names, ID numbers and the ID photo are for the Super Admin only. The check is here as well as in the
        // screen, so no other screen can show this data by mistake.
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isSuperAdmin()) return null;

        String sql = "SELECT t.transaction_id, t.borrower_name, t.borrower_type, t.borrower_id_number, " +
                "t.program_or_dept, t.id_snapshot, u_req.username AS requester_username " +
                "FROM transactions t " +
                "LEFT JOIN users u_req ON t.requested_by = u_req.user_id " +
                "WHERE t.transaction_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                return new BorrowerDetails(
                        rs.getInt("transaction_id"),
                        rs.getString("borrower_name"),
                        rs.getString("borrower_type"),
                        rs.getString("borrower_id_number"),
                        rs.getString("program_or_dept"),
                        rs.getString("requester_username"),
                        rs.getBytes("id_snapshot"));
            }
        } catch (SQLException e) {
            System.err.println("[EquipmentDAOImpl Error] Failed to load borrower details for tx " + transactionId + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public Receipt getReceiptForTransaction(int transactionId) {
        String sql = "SELECT t.transaction_id, t.status, t.borrower_name, t.borrower_type, t.borrower_id_number, " +
                "t.program_or_dept, t.date_borrowed, t.expected_return_date, t.actual_return_date, " +
                "t.total_fee_charged, t.late_penalty_per_day, t.initial_condition, " +
                "e.item_name, e.category, e.serial_number, " +
                "u_req.username AS requester_username, u_proc.username AS processor_username " +
                "FROM transactions t " +
                "JOIN equipment e ON t.equipment_id = e.equipment_id " +
                "LEFT JOIN users u_req ON t.requested_by = u_req.user_id " +
                "LEFT JOIN users u_proc ON t.processed_by = u_proc.user_id " +
                "WHERE t.transaction_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;

                java.sql.Date borrowed = rs.getDate("date_borrowed");
                java.sql.Date due = rs.getDate("expected_return_date");
                java.sql.Date returned = rs.getDate("actual_return_date");

                return new Receipt(
                        rs.getInt("transaction_id"),
                        receiptStatusLabel(rs.getString("status"), due != null ? due.toLocalDate() : null),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getString("serial_number"),
                        rs.getString("borrower_name"),
                        rs.getString("borrower_type"),
                        rs.getString("borrower_id_number"),
                        rs.getString("program_or_dept"),
                        borrowed != null ? borrowed.toLocalDate() : null,
                        due != null ? due.toLocalDate() : null,
                        returned != null ? returned.toLocalDate() : null,
                        rs.getDouble("total_fee_charged"),
                        rs.getDouble("late_penalty_per_day"),
                        rs.getString("initial_condition"),
                        rs.getString("requester_username"),
                        rs.getString("processor_username")
                );
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private static String receiptStatusLabel(String rawStatus, LocalDate due) {
        if ("RETURNED".equalsIgnoreCase(rawStatus)) return "Returned";
        if ("REJECTED".equalsIgnoreCase(rawStatus)) return "Rejected";
        if ("CANCELLED".equalsIgnoreCase(rawStatus)) return "Cancelled";
        if ("RESERVED".equalsIgnoreCase(rawStatus)) return "Reserved";
        if ("PENDING RETURN".equalsIgnoreCase(rawStatus)) return "Pending Return";
        if ("BORROWED".equalsIgnoreCase(rawStatus)) {
            return (due != null && due.isBefore(LocalDate.now())) ? "Overdue" : "Active";
        }
        return "Pending";
    }
}