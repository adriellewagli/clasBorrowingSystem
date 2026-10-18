package controller.auth;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import db.DatabaseConnection;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javafx.scene.control.Alert;

public class ForgotPasswordController extends BaseController {

    @FXML private VBox cardContainer;
    @FXML private TextField txtUsername;
    @FXML private Button btnResetPassword;
    @FXML private Label lblMessage;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();

    @FXML
    public void initialize() {
        if (cardContainer != null) {
            Platform.runLater(() -> playSlideFadeIn(cardContainer));
        }
    }

    @FXML
    private void handleResetPassword(ActionEvent event) {
        String username = txtUsername.getText().trim();

        // 1. Validate Form Input
        if (username.isEmpty()) {
            setMessage("Please enter your username.", false);
            playErrorShake(cardContainer);
            return;
        }

        // 2. Fetch User ID from Username
        int userId = getUserIdByUsername(username);
        if (userId == -1) {
            setMessage("Username does not exist in the database.", false);
            playErrorShake(cardContainer);
            return;
        }

        // 3. Submit Password Reset Ticket
        String errorMsg = equipmentDAO.createPasswordTicket(userId);

        if (errorMsg == null) {
            // Use your project's built-in success dialog method!
            String successMsg = String.format("Password reset ticket submitted successfully!\n\nA SuperAdmin will review and approve your request.\n\nOnce approved, your temporary password will be: %s123!", username);

            showSuccessDialog("Request Submitted", successMsg);

            // Clear the field and reset message
            txtUsername.clear();
            if (lblMessage != null) lblMessage.setVisible(false);
        } else {
            setMessage(errorMsg, false); // Displays "You already requested a ticket."
            playErrorShake(cardContainer);
        }
    }

    private int getUserIdByUsername(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("user_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
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