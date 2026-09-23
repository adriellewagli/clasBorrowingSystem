package controller.dialog;

import controller.BaseController;
import db.DatabaseConnection;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SuperAdminAuthDialogController extends BaseController {

    @FXML private VBox cardContainer;
    @FXML private Label lblActionDescription;
    @FXML private TextField txtAdminUsername;
    @FXML private PasswordField txtAdminPassword;
    @FXML private Label lblMessage;

    private boolean authenticated = false;

    @FXML
    public void initialize() {
        if (cardContainer != null) {
            Platform.runLater(() -> playSlideFadeIn(cardContainer));
        }
    }

    public void setActionDescription(String description) {
        if (lblActionDescription != null && description != null && !description.isEmpty()) {
            lblActionDescription.setText(description);
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    @FXML
    private void handleAuthenticate(ActionEvent event) {
        String username = txtAdminUsername.getText().trim();
        String password = txtAdminPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            setMessage("Please fill in all credentials.", false);
            playErrorShake(cardContainer);
            return;
        }

        String query = "SELECT user_id FROM users WHERE username = ? AND password = ? AND role = 'SUPER_ADMIN' AND status = 'ACTIVE'";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);
            stmt.setString(2, password);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                this.authenticated = true;
                closeStage();
            } else {
                setMessage("Invalid Super Admin credentials or insufficient privileges.", false);
                playErrorShake(cardContainer);
            }

        } catch (SQLException e) {
            setMessage("Database connection error.", false);
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        this.authenticated = false;
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) cardContainer.getScene().getWindow();
        if (stage != null) {
            stage.close();
        }
    }

    private void setMessage(String text, boolean success) {
        lblMessage.setText(text);
        lblMessage.setStyle(success ? "-fx-text-fill: #10b981;" : "-fx-text-fill: #ef4444;");
    }
}