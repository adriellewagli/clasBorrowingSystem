package model;

public class UserSession {

    private static UserSession instance;

    private int userId;
    private String fullName;
    private String username;
    private String role;
    private String department; // Added department field

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    // Overloaded setSession for initial login (4 parameters)
    public static void setSession(int id, String name, String user, String userRole) {
        setSession(id, name, user, userRole, null);
    }

    // Full setSession including department (5 parameters)
    public static void setSession(int id, String name, String user, String userRole, String dept) {
        UserSession session = getInstance();
        session.userId = id;
        session.fullName = name;
        session.username = user;
        session.role = userRole;
        session.department = dept;
    }

    public int getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    // Department Getter & Setter
    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public static void cleanUserSession() {
        if (instance != null) {
            instance.userId = 0;
            instance.fullName = null;
            instance.username = null;
            instance.role = null;
            instance.department = null;
        }
    }
}