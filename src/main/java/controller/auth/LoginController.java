package controller.auth;

import controller.BaseController;
import db.DatabaseConnection;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import dao.UserDAO;
import model.UserSession;
import util.CredentialsDialog;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController extends BaseController {

    @FXML private VBox cardContainer;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordShown;
    @FXML private Button btnTogglePassword;
    @FXML private SVGPath svgEyeIcon;
    @FXML private Label lblMessage;

    private boolean isPasswordVisible = false;

    // SVG Icon Paths
    private static final String EYE_OPEN_PATH = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String EYE_CLOSED_PATH = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2z";

    @FXML
    public void initialize() {
        if (cardContainer != null) {
            Platform.runLater(() -> playSlideFadeIn(cardContainer));
        }
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        System.out.println("--- [DEBUG] handleLogin triggered ---");

        String username = txtUsername.getText().trim();
        String password = getEnteredPassword();

        System.out.println("[DEBUG] Entered Username: '" + username + "'");

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("[DEBUG] Validation failed: Empty fields.");
            lblMessage.setText("Please enter both username and password.");
            playErrorShake(cardContainer);
            return;
        }

        String query = "SELECT user_id, full_name, username, role, status FROM users WHERE username = ? AND password = ?";

        System.out.println("[DEBUG] Connecting to MySQL database...");
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("[DEBUG ERROR] Database connection returned NULL!");
                lblMessage.setText("Database connection failed.");
                return;
            }
            System.out.println("[DEBUG] Connected to database successfully.");

            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, password);

                System.out.println("[DEBUG] Executing SQL Query...");
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    int userId = rs.getInt("user_id");
                    String fullName = rs.getString("full_name");
                    String dbUsername = rs.getString("username");
                    String role = rs.getString("role");
                    String status = rs.getString("status");

                    System.out.println("[DEBUG] User Found! ID: " + userId + " | Role: " + role + " | Status: " + status);

                    if ("INACTIVE".equalsIgnoreCase(status)) {
                        System.out.println("[DEBUG] Login denied: Account is INACTIVE.");
                        lblMessage.setText("Your account is deactivated. Contact a Super Admin.");
                        playErrorShake(cardContainer);
                        return;
                    }

                    // Set User Session
                    UserSession.setSession(userId, fullName, dbUsername, role);
                    System.out.println("[DEBUG] UserSession set successfully.");

                    // Accounts created by a Super Admin start with a default username/password.
                    // Offer to change them now; "Later" keeps the defaults and we ask again next login.
                    if (new UserDAO().usesDefaultCredentials(userId)) {
                        CredentialsDialog.show(cardContainer.getScene().getWindow(), true);
                    }

                    // Dynamic Role-Based Routing
                    // The Super Admin uses the admin dashboard, which adds a "Manage Accounts" tab for that role.
                    String dashboardPath;
                    if ("SUPERADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)
                            || "ADMIN".equalsIgnoreCase(role)) {
                        dashboardPath = "/com/borrowclas/clasborrowingsystem/fxml/admin/admin_dashboard.fxml";
                    } else {
                        dashboardPath = "/com/borrowclas/clasborrowingsystem/fxml/user/user_dashboard.fxml";
                    }

                    System.out.println("[DEBUG] Navigating to: " + dashboardPath);
                    navigateTo(dashboardPath);
                    System.out.println("[DEBUG] Navigation executed!");

                } else {
                    System.out.println("[DEBUG] Login failed: Invalid username or password.");
                    lblMessage.setText("Invalid username or password.");
                    playErrorShake(cardContainer);
                }
            }

        } catch (SQLException e) {
            System.err.println("[DEBUG ERROR] SQL Exception caught!");
            lblMessage.setText("Database error during login.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("[DEBUG ERROR] IOException caught! Check FXML dashboard paths.");
            lblMessage.setText("Failed to load Dashboard view.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[DEBUG ERROR] Unexpected Exception caught!");
            e.printStackTrace();
        }
    }

    @FXML
    private void focusPassword(ActionEvent event) {
        if (isPasswordVisible) {
            txtPasswordShown.requestFocus();
        } else {
            txtPassword.requestFocus();
        }
    }

    @FXML
    private void togglePasswordVisibility(ActionEvent event) {
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            txtPasswordShown.setText(txtPassword.getText());
            txtPasswordShown.setVisible(true);
            txtPasswordShown.setManaged(true);
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
            if (svgEyeIcon != null) {
                svgEyeIcon.setContent(EYE_CLOSED_PATH);
            }
            txtPasswordShown.requestFocus();
            txtPasswordShown.selectEnd();
        } else {
            txtPassword.setText(txtPasswordShown.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
            txtPasswordShown.setVisible(false);
            txtPasswordShown.setManaged(false);
            if (svgEyeIcon != null) {
                svgEyeIcon.setContent(EYE_OPEN_PATH);
            }
            txtPassword.requestFocus();
            txtPassword.selectEnd();
        }
    }

    private String getEnteredPassword() {
        return isPasswordVisible ? txtPasswordShown.getText().trim() : txtPassword.getText().trim();
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        try {
            navigateTo("/com/borrowclas/clasborrowingsystem/fxml/auth/forgot_password.fxml");
        } catch (IOException e) {
            lblMessage.setText("Failed to load Forgot Password screen.");
            e.printStackTrace();
        }
    }
}