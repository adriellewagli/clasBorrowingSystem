package controller;

import controller.dialog.SuperAdminAuthDialogController;
import db.DatabaseConnection;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

public class CreateAccountController extends BaseController {

    @FXML private VBox cardContainer;
    @FXML private TextField txtFullName;
    @FXML private TextField txtUsername;
    @FXML private ComboBox<String> cmbRole;
    @FXML private PasswordField txtPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private Button btnRegister;
    @FXML private Label lblMessage;

    @FXML
    public void initialize() {
        // Aligned with Proposal Roles: USER, ADMIN, SUPERADMIN
        cmbRole.setItems(FXCollections.observableArrayList("USER", "ADMIN", "SUPERADMIN"));
        cmbRole.getSelectionModel().select("USER");

        if (cardContainer != null) {
            Platform.runLater(() -> playSlideFadeIn(cardContainer));
        }
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        String fullName = txtFullName.getText().trim();
        String username = txtUsername.getText().trim();
        String role = cmbRole.getValue();
        String password = txtPassword.getText().trim();
        String confirmPassword = txtConfirmPassword.getText().trim();

        // 1. Validation Checks
        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || role == null) {
            setStatus("Please fill in all required fields.", false);
            playErrorShake(cardContainer);
            return;
        }

        if (!password.equals(confirmPassword)) {
            setStatus("Passwords do not match.", false);
            playErrorShake(cardContainer);
            return;
        }

        if (password.length() < 4) {
            setStatus("Password must be at least 4 characters long.", false);
            playErrorShake(cardContainer);
            return;
        }

        // 2. Verify Username Uniqueness
        if (isUsernameTaken(username)) {
            setStatus("Username is already taken. Choose another.", false);
            playErrorShake(cardContainer);
            return;
        }

        // 3. Super Admin Verification Dialog (Per Proposal Control Rule)
        boolean authorized = requestSuperAdminApproval("Authorize creation of new " + role + " account for: " + username);

        if (!authorized) {
            setStatus("Account registration cancelled or unauthorized.", false);
            return;
        }

        // 4. Save to MySQL Central Database
        String insertUserSql = "INSERT INTO users (full_name, username, password, role, status) VALUES (?, ?, ?, ?, 'ACTIVE')";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement insertStmt = conn.prepareStatement(insertUserSql)) {

            insertStmt.setString(1, fullName);
            insertStmt.setString(2, username);
            insertStmt.setString(3, password);
            insertStmt.setString(4, role);
            insertStmt.executeUpdate();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText("Account Created");
            alert.setContentText("The " + role + " account for " + fullName + " has been successfully created!");
            alert.showAndWait();

            handleBackToLogin(event);

        } catch (SQLException e) {
            setStatus("Database error during account creation.", false);
            e.printStackTrace();
        }
    }

    private boolean isUsernameTaken(String username) {
        String checkUsernameSql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkUsernameSql)) {
            checkStmt.setString(1, username);
            ResultSet rs = checkStmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
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
            setStatus("Failed to load Super Admin authorization window.", false);
            e.printStackTrace();
            return false;
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        try {
            navigateTo("/com/borrowclas/clasborrowingsystem/fxml/login.fxml");
        } catch (IOException e) {
            setStatus("Failed to load login screen.", false);
            e.printStackTrace();
        }
    }

    private void setStatus(String message, boolean success) {
        lblMessage.setStyle(success ? "-fx-text-fill: #10b981;" : "-fx-text-fill: #ef4444;");
        lblMessage.setText(message);
        lblMessage.setVisible(true);
    }
}