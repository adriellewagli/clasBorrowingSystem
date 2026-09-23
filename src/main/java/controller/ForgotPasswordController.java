package controller;

import controller.dialog.SuperAdminAuthDialogController;
import db.DatabaseConnection;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ForgotPasswordController extends BaseController {

    @FXML private VBox cardContainer;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private Button btnResetPassword;
    @FXML private Label lblMessage;

    @FXML
    public void initialize() {
        if (cardContainer != null) {
            Platform.runLater(() -> playSlideFadeIn(cardContainer));
        }
    }

    @FXML
    private void handleResetPassword(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String newPassword = txtNewPassword.getText().trim();
        String confirmPassword = txtConfirmPassword.getText().trim();

        // 1. Validate Form Input
        if (username.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            setMessage("Please fill in all fields.", false);
            playErrorShake(cardContainer);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            setMessage("Passwords do not match.", false);
            playErrorShake(cardContainer);
            return;
        }

        if (newPassword.length() < 4) {
            setMessage("Password must be at least 4 characters long.", false);
            playErrorShake(cardContainer);
            return;
        }

        // 2. Check if Username Exists
        if (!userExists(username)) {
            setMessage("Username does not exist in the database.", false);
            playErrorShake(cardContainer);
            return;
        }

        // 3. Request Super Admin Authorization Modal
        boolean authorized = requestSuperAdminApproval("Authorize password update for user: " + username);

        if (!authorized) {
            setMessage("Authorization cancelled or failed.", false);
            return;
        }

        // 4. Update Password in Database
        if (updatePasswordInDb(username, newPassword)) {
            setMessage("Password successfully updated! You can now log in.", true);
            txtNewPassword.clear();
            txtConfirmPassword.clear();
        } else {
            setMessage("Failed to update password due to a database error.", false);
            playErrorShake(cardContainer);
        }
    }

    private boolean requestSuperAdminApproval(String actionDescription) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/superadmin_auth_dialog.fxml"));
            Parent root = loader.load();

            SuperAdminAuthDialogController controller = loader.getController();
            controller.setActionDescription(actionDescription);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Super Admin Verification");
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            // Set parent window owner for proper modality blocking
            if (cardContainer != null && cardContainer.getScene() != null) {
                dialogStage.initOwner(cardContainer.getScene().getWindow());
            }

            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);
            dialogStage.showAndWait();

            return controller.isAuthenticated();

        } catch (IOException e) {
            setMessage("Could not load Super Admin verification window.", false);
            e.printStackTrace();
            return false;
        }
    }

    private boolean userExists(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean updatePasswordInDb(String username, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        try {
            navigateTo("/com/borrowclas/clasborrowingsystem/fxml/login.fxml");
        } catch (IOException e) {
            setMessage("Failed to navigate back to Login.", false);
            e.printStackTrace();
        }
    }

    private void setMessage(String text, boolean success) {
        lblMessage.setText(text);
        lblMessage.setStyle(success ? "-fx-text-fill: #10b981;" : "-fx-text-fill: #ef4444;");
        lblMessage.setVisible(true);
    }
}