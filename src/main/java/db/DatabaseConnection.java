package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/clas_borrowing_db";
    private static final String USER = "root";
    private static final String PASSWORD = ""; // Empty string for default XAMPP setup

    private static boolean schemaChecked = false;

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
        ensureSchema(conn);
        return conn;
    }

    /**
     * Runs once per launch and quietly brings the database up to what the app expects:
     *  - status / role columns that are ENUMs become VARCHAR(20) so new values ('PENDING RETURN',
     *    'MAINTENANCE', 'SUPERADMIN') are accepted. Existing values are kept.
     *  - users.uses_default_credentials is added (1 = still on the default username/password).
     *  - if there is no Super Admin account yet, a first one is created (superadmin / superadmin123).
     * Every step logs a message instead of failing if it can't be done (e.g. no ALTER permission).
     */
    private static synchronized void ensureSchema(Connection conn) {
        if (schemaChecked) return;
        schemaChecked = true;

        fixVarcharColumn(conn, "transactions", "status", "PENDING");
        fixVarcharColumn(conn, "equipment", "status", "AVAILABLE");
        fixVarcharColumn(conn, "users", "role", "USER");
        fixVarcharColumn(conn, "users", "status", "ACTIVE");
        addDefaultCredentialsFlag(conn);
        seedFirstSuperAdmin(conn);
    }

    private static void fixVarcharColumn(Connection conn, String table, String column, String fallbackDefault) {
        String info = "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";

        try (PreparedStatement ps = conn.prepareStatement(info)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;

                String type = rs.getString("DATA_TYPE");
                long length = rs.getLong("CHARACTER_MAXIMUM_LENGTH");
                boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                String def = rs.getString("COLUMN_DEFAULT");

                boolean isEnum = "enum".equalsIgnoreCase(type);
                boolean tooShort = ("varchar".equalsIgnoreCase(type) || "char".equalsIgnoreCase(type)) && length < 20;
                if (!isEnum && !tooShort) return; // already flexible enough

                StringBuilder alter = new StringBuilder("ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(20)");
                alter.append(nullable ? " NULL" : " NOT NULL");
                if (def != null) {
                    alter.append(" DEFAULT '").append(def.replace("'", "''")).append("'");
                } else if (!nullable) {
                    alter.append(" DEFAULT '").append(fallbackDefault).append("'");
                }

                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(alter.toString());
                    System.out.println("[DatabaseConnection] Updated " + table + "." + column + " to VARCHAR(20).");
                }
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Could not update " + table + "." + column + " automatically: " + e.getMessage()
                    + " - run: ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(20) NOT NULL;");
        }
    }

    private static void addDefaultCredentialsFlag(Connection conn) {
        String check = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'uses_default_credentials'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(check)) {
            if (rs.next() && rs.getInt(1) > 0) return;
            st.executeUpdate("ALTER TABLE users ADD COLUMN uses_default_credentials TINYINT(1) NOT NULL DEFAULT 0");
            System.out.println("[DatabaseConnection] Added users.uses_default_credentials.");
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Could not add users.uses_default_credentials: " + e.getMessage()
                    + " - run: ALTER TABLE users ADD COLUMN uses_default_credentials TINYINT(1) NOT NULL DEFAULT 0;");
        }
    }

    private static void seedFirstSuperAdmin(Connection conn) {
        String count = "SELECT COUNT(*) FROM users WHERE UPPER(REPLACE(role, '_', '')) = 'SUPERADMIN'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(count)) {
            if (rs.next() && rs.getInt(1) > 0) return;
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Could not check for a Super Admin: " + e.getMessage());
            return;
        }

        String withFlag = "INSERT INTO users (full_name, username, password, role, status, uses_default_credentials) " +
                "VALUES ('Super Administrator', 'superadmin', 'superadmin123', 'SUPERADMIN', 'ACTIVE', 1)";
        String withoutFlag = "INSERT INTO users (full_name, username, password, role, status) " +
                "VALUES ('Super Administrator', 'superadmin', 'superadmin123', 'SUPERADMIN', 'ACTIVE')";
        try (Statement st = conn.createStatement()) {
            try {
                st.executeUpdate(withFlag);
            } catch (SQLException first) {
                st.executeUpdate(withoutFlag);
            }
            System.out.println("[DatabaseConnection] No Super Admin existed - created 'superadmin' / 'superadmin123'. Change it after logging in.");
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Could not create the first Super Admin: " + e.getMessage());
        }
    }
}
