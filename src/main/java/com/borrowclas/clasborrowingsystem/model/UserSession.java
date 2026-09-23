package model;

public class UserSession {
    private static UserSession instance;

    private int userId;
    private String fullName;
    private String username;
    private String role; // "USER", "ADMIN", "SUPERADMIN"

    private UserSession(int userId, String fullName, String username, String role) {
        this.userId = userId;
        this.fullName = fullName;
        this.username = username;
        this.role = role;
    }

    public static void setSession(int userId, String fullName, String username, String role) {
        instance = new UserSession(userId, fullName, username, role);
    }

    public static UserSession getInstance() {
        return instance;
    }

    public static void cleanUserSession() {
        instance = null;
    }

    public int getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
}