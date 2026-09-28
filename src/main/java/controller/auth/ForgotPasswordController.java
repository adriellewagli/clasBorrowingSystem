package controller.auth;

import controller.BaseController;
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
import javafx.scene.shape.SVGPath;
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

    // New Password Fields & Controls
    @FXML private PasswordField txtNewPassword;
    @FXML private TextField txtNewPasswordShown;
    @FXML private Button btnToggleNewPassword;
    @FXML private SVGPath svgEyeIconNew;

    // Confirm Password Fields & Controls
    @FXML private PasswordField txtConfirmPassword;
    @FXML private TextField txtConfirmPasswordShown;
    @FXML private Button btnToggleConfirmPassword;
    @FXML private SVGPath svgEyeIconConfirm;

    @FXML private Button btnResetPassword;
    @FXML private Label lblMessage;

    private boolean isNewPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    // SVG Icon Paths
    private static final String EYE_OPEN_PATH = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String EYE_CLOSED_PATH = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2z";

    @FXML
    public void initialize() {
        if (cardContainer != null) {
            Platform.runLater(() -> playSlideFadeIn(cardContainer));
        }
    }

    // ==========================================================================
    // ENTER KEY NAVIGATION FLOW
    // ==========================================================================

    @FXML
    private void focusNewPassword(ActionEvent event) {
        if (isNewPasswordVisible) {
            txtNewPasswordShown.requestFocus();
        } else {
            txtNewPassword.requestFocus();
        }
    }

    @FXML
    private void focusConfirmPassword(ActionEvent event) {
        if (isConfirmPasswordVisible) {
            txtConfirmPasswordShown.requestFocus();
        } else {
            txtConfirmPassword.requestFocus();
        }
    }

    // ==========================================================================
    // PASSWORD VISIBILITY TOGGLES
    // ==========================================================================

    @FXML
    private void toggleNewPasswordVisibility(ActionEvent event) {
        isNewPasswordVisible = !isNewPasswordVisible;

        if (isNewPasswordVisible) {
            txtNewPasswordShown.setText(txtNewPassword.getText());
            txtNewPasswordShown.setVisible(true);
            txtNewPasswordShown.setManaged(true);
            txtNewPassword.setVisible(false);
            txtNewPassword.setManaged(false);
            if (svgEyeIconNew != null) {
                svgEyeIconNew.setContent(EYE_CLOSED_PATH);
            }
            txtNewPasswordShown.requestFocus();
            txtNewPasswordShown.selectEnd();
        } else {
            txtNewPassword.setText(txtNewPasswordShown.getText());
            txtNewPassword.setVisible(true);
            txtNewPassword.setManaged(true);
            txtNewPasswordShown.setVisible(false);
            txtNewPasswordShown.setManaged(false);
            if (svgEyeIconNew != null) {
                svgEyeIconNew.setContent(EYE_OPEN_PATH);
            }
            txtNewPassword.requestFocus();
            txtNewPassword.selectEnd();
        }
    }

    @FXML
    private void toggleConfirmPasswordVisibility(ActionEvent event) {
        isConfirmPasswordVisible = !isConfirmPasswordVisible;

        if (isConfirmPasswordVisible) {
            txtConfirmPasswordShown.setText(txtConfirmPassword.getText());
            txtConfirmPasswordShown.setVisible(true);
            txtConfirmPasswordShown.setManaged(true);
            txtConfirmPassword.setVisible(false);
            txtConfirmPassword.setManaged(false);
            if (svgEyeIconConfirm != null) {
                svgEyeIconConfirm.setContent(EYE_CLOSED_PATH);
            }
            txtConfirmPasswordShown.requestFocus();
            txtConfirmPasswordShown.selectEnd();
        } else {
            txtConfirmPassword.setText(txtConfirmPasswordShown.getText());
            txtConfirmPassword.setVisible(true);
            txtConfirmPassword.setManaged(true);
            txtConfirmPasswordShown.setVisible(false);
            txtConfirmPasswordShown.setManaged(false);
            if (svgEyeIconConfirm != null) {
                svgEyeIconConfirm.setContent(EYE_OPEN_PATH);
            }
            txtConfirmPassword.requestFocus();
            txtConfirmPassword.selectEnd();
        }
    }

    // ==========================================================================
    // ACTION HANDLERS
    // ==========================================================================

    @FXML
    private void handleResetPassword(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String newPassword = isNewPasswordVisible ? txtNewPasswordShown.getText().trim() : txtNewPassword.getText().trim();
        String confirmPassword = isConfirmPasswordVisible ? txtConfirmPasswordShown.getText().trim() : txtConfirmPassword.getText().trim();

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
            txtNewPasswordShown.clear();
            txtConfirmPassword.clear();
            txtConfirmPasswordShown.clear();
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
            navigateTo("/com/borrowclas/clasborrowingsystem/fxml/auth/login.fxml");
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