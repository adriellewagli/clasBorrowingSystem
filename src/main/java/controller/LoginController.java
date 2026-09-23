package controller;

import db.DatabaseConnection;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import model.UserSession;

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
    @FXML private Label lblMessage;

    private boolean isPasswordVisible = false;

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
        String password = isPasswordVisible ? txtPasswordShown.getText().trim() : txtPassword.getText().trim();

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

                    // Navigate to Dashboard
                    String dashboardPath = "/com/borrowclas/clasborrowingsystem/fxml/dashboard.fxml";
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
            System.err.println("[DEBUG ERROR] IOException caught! Check dashboard.fxml path!");
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
            btnTogglePassword.getStyleClass().removeAll("eye-open", "eye-closed");
            btnTogglePassword.getStyleClass().add("eye-closed");
            txtPasswordShown.requestFocus();
            txtPasswordShown.selectEnd();
        } else {
            txtPassword.setText(txtPasswordShown.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
            txtPasswordShown.setVisible(false);
            txtPasswordShown.setManaged(false);
            btnTogglePassword.getStyleClass().removeAll("eye-open", "eye-closed");
            btnTogglePassword.getStyleClass().add("eye-open");
            txtPassword.requestFocus();
            txtPassword.selectEnd();
        }
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        try {
            navigateTo("/com/borrowclas/clasborrowingsystem/fxml/forgot_password.fxml");
        } catch (IOException e) {
            lblMessage.setText("Failed to load Forgot Password screen.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleSignUp(ActionEvent event) {
        try {
            navigateTo("/com/borrowclas/clasborrowingsystem/fxml/create_account.fxml");
        } catch (IOException e) {
            lblMessage.setText("Failed to load Create Account screen.");
            e.printStackTrace();
        }
    }
}