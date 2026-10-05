package dao;

import db.DatabaseConnection;
import model.UserAccount;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Account management. Methods that change data return null on success, or a short
 * message explaining why it was refused - the screens show that message as is.
 * Passwords are stored the same way the login screen compares them (plain text).
 */
public class UserDAO {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SUPER_ADMIN = "SUPERADMIN";

    private static final SecureRandom RANDOM = new SecureRandom();

    // ------------------------------------------------------------ reading

    public List<UserAccount> getAllUsers() {
        List<UserAccount> list = new ArrayList<>();
        String sql = "SELECT user_id, full_name, username, role, status, uses_default_credentials " +
                "FROM users ORDER BY user_id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new UserAccount(
                        rs.getInt("user_id"),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        normalizeRole(rs.getString("role")),
                        !"INACTIVE".equalsIgnoreCase(rs.getString("status")),
                        rs.getInt("uses_default_credentials") == 1));
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] Failed to load accounts: " + e.getMessage());
        }
        return list;
    }

    /** True while the account still has the default login the Super Admin gave it. */
    public boolean usesDefaultCredentials(int userId) {
        String sql = "SELECT uses_default_credentials FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            return false; // never block a login because of this check
        }
    }

    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ defaults

    /** Next free default username for the role: user001, admin001, super001, ... */
    public String generateUsername(String role) {
        String prefix = switch (normalizeRole(role)) {
            case ROLE_ADMIN -> "admin";
            case ROLE_SUPER_ADMIN -> "super";
            default -> "user";
        };

        int max = 0;
        Pattern p = Pattern.compile("^" + prefix + "(\\d{1,6})$", Pattern.CASE_INSENSITIVE);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT username FROM users WHERE username LIKE ?")) {
            stmt.setString(1, prefix + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Matcher m = p.matcher(rs.getString(1));
                    if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] Could not read usernames: " + e.getMessage());
        }

        int next = max + 1;
        String candidate;
        do {
            candidate = String.format("%s%03d", prefix, next++);
        } while (usernameExists(candidate) && next < 1_000_000);
        return candidate;
    }

    /** A random default password such as CLAS-4821. */
    public String generateDefaultPassword() {
        return "CLAS-" + (1000 + RANDOM.nextInt(9000));
    }

    // ------------------------------------------------------------ changes

    public String createUser(String fullName, String username, String password, String role) {
        String r = normalizeRole(role);
        if (usernameExists(username)) return "That username is already taken.";

        String sql = "INSERT INTO users (full_name, username, password, role, status, uses_default_credentials) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', 1)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fullName);
            stmt.setString(2, username);
            stmt.setString(3, password);
            stmt.setString(4, r);
            stmt.executeUpdate();
            return null;
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] Failed to create account: " + e.getMessage());
            return "Could not create the account: " + e.getMessage();
        }
    }

    /** Activate or deactivate an account. Deactivated accounts can't log in. */
    public String setActive(int userId, boolean active, int actingUserId) {
        if (userId == actingUserId) return "You can't change the status of your own account.";

        try (Connection conn = DatabaseConnection.getConnection()) {
            String role;
            boolean currentlyActive;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT role, status FROM users WHERE user_id = ?")) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return "That account no longer exists.";
                    role = normalizeRole(rs.getString("role"));
                    currentlyActive = !"INACTIVE".equalsIgnoreCase(rs.getString("status"));
                }
            }

            if (!active && currentlyActive && ROLE_SUPER_ADMIN.equals(role) && activeSuperAdmins(conn) <= 1) {
                return "At least one active Super Admin account must remain.";
            }

            try (PreparedStatement stmt = conn.prepareStatement("UPDATE users SET status = ? WHERE user_id = ?")) {
                stmt.setString(1, active ? "ACTIVE" : "INACTIVE");
                stmt.setInt(2, userId);
                stmt.executeUpdate();
            }
            return null;
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] Failed to change status: " + e.getMessage());
            return "Database error: " + e.getMessage();
        }
    }

    /**
     * Permanently deletes an account. Accounts that already appear in borrowing records are refused
     * so the audit history stays intact - deactivate those instead.
     */
    public String deleteUser(int userId, int actingUserId) {
        if (userId == actingUserId) return "You can't delete your own account.";

        try (Connection conn = DatabaseConnection.getConnection()) {
            String role;
            boolean currentlyActive;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT role, status FROM users WHERE user_id = ?")) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return "That account no longer exists.";
                    role = normalizeRole(rs.getString("role"));
                    currentlyActive = !"INACTIVE".equalsIgnoreCase(rs.getString("status"));
                }
            }

            if (currentlyActive && ROLE_SUPER_ADMIN.equals(role) && activeSuperAdmins(conn) <= 1) {
                return "At least one active Super Admin account must remain.";
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM transactions WHERE requested_by = ? OR processed_by = ?")) {
                stmt.setInt(1, userId);
                stmt.setInt(2, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return "This account appears in borrowing records, so it can't be deleted. Deactivate it instead.";
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                stmt.setInt(1, userId);
                stmt.executeUpdate();
            }
            return null;
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] Failed to delete account: " + e.getMessage());
            return "This account can't be deleted because other records still use it. Deactivate it instead.";
        }
    }

    /**
     * Lets a signed-in user change their own username and/or password.
     * @param newPassword null or empty to keep the current password.
     * Changing the password also clears the "default login details" flag.
     */
    public String changeCredentials(int userId, String newUsername, String currentPassword, String newPassword) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            String oldUsername;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT username, password FROM users WHERE user_id = ?")) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return "Your account could not be found.";
                    if (!currentPassword.equals(rs.getString("password"))) return "Your current password is incorrect.";
                    oldUsername = rs.getString("username");
                }
            }

            if (!newUsername.equalsIgnoreCase(oldUsername)) {
                try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM users WHERE username = ? AND user_id <> ?")) {
                    stmt.setString(1, newUsername);
                    stmt.setInt(2, userId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) return "That username is already taken.";
                    }
                }
            }

            boolean changePassword = newPassword != null && !newPassword.isEmpty();
            String sql = changePassword
                    ? "UPDATE users SET username = ?, password = ?, uses_default_credentials = 0 WHERE user_id = ?"
                    : "UPDATE users SET username = ? WHERE user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newUsername);
                if (changePassword) {
                    stmt.setString(2, newPassword);
                    stmt.setInt(3, userId);
                } else {
                    stmt.setInt(2, userId);
                }
                stmt.executeUpdate();
            }
            return null;
        } catch (SQLException e) {
            System.err.println("[UserDAO Error] Failed to change credentials: " + e.getMessage());
            return "Database error: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------ helpers

    private int activeSuperAdmins(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE UPPER(REPLACE(role, '_', '')) = 'SUPERADMIN' AND UPPER(status) = 'ACTIVE'";
        try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static String normalizeRole(String role) {
        if (role == null) return ROLE_USER;
        String r = role.replace("_", "").trim().toUpperCase();
        if (r.equals("SUPERADMIN")) return ROLE_SUPER_ADMIN;
        if (r.equals("ADMIN")) return ROLE_ADMIN;
        return ROLE_USER;
    }
}
