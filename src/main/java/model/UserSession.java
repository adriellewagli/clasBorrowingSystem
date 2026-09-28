package model;

public class UserSession {

    private static UserSession instance;

    private int userId;
    private String fullName;
    private String username;
    private String role;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public static void setSession(int id, String name, String user, String userRole) {
        UserSession session = getInstance();
        session.userId = id;
        session.fullName = name;
        session.username = user;
        session.role = userRole;
    }

    public int getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public static void cleanUserSession() {
        if (instance != null) {
            instance.userId = 0;
            instance.fullName = null;
            instance.username = null;
            instance.role = null;
        }
    }
}