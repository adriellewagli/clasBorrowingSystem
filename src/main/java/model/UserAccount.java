package model;

/** One row of the Account Management table. */
public class UserAccount {
    private final int userId;
    private final String fullName;
    private final String username;
    private final String role;       // USER, ADMIN or SUPERADMIN
    private final boolean active;
    private final boolean defaultCredentials;

    public UserAccount(int userId, String fullName, String username, String role,
                       boolean active, boolean defaultCredentials) {
        this.userId = userId;
        this.fullName = fullName;
        this.username = username;
        this.role = role;
        this.active = active;
        this.defaultCredentials = defaultCredentials;
    }

    public int getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isDefaultCredentials() { return defaultCredentials; }

    /** Text for the STATUS column ("Deactivated" avoids matching the green "active" pill colour). */
    public String getStatus() { return active ? "Active" : "Deactivated"; }

    /** Text for the LOGIN DETAILS column. */
    public String getCredentialState() { return defaultCredentials ? "Default" : "Customized"; }

    /** Role as shown to people: SUPERADMIN -> Super Admin. */
    public String getRoleLabel() {
        String r = role == null ? "" : role.replace("_", "").toUpperCase();
        return switch (r) {
            case "SUPERADMIN" -> "Super Admin";
            case "ADMIN" -> "Admin";
            default -> "User";
        };
    }
}
