package ui;

import db.DatabaseConnection;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtPasswordShown;
    @FXML private Button btnTogglePassword;
    @FXML private Button btnLogin;
    @FXML private Label lblMessage;

    private boolean isPasswordVisible = false;

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
        if (isPasswordVisible) {
            // Hide password
            txtPassword.setText(txtPasswordShown.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);

            txtPasswordShown.setVisible(false);
            txtPasswordShown.setManaged(false);

            btnTogglePassword.setText("Show");
            isPasswordVisible = false;
            txtPassword.requestFocus();
            txtPassword.selectEnd();
        } else {
            // Show password
            txtPasswordShown.setText(txtPassword.getText());
            txtPasswordShown.setVisible(true);
            txtPasswordShown.setManaged(true);

            txtPassword.setVisible(false);
            txtPassword.setManaged(false);

            btnTogglePassword.setText("Hide");
            isPasswordVisible = true;
            txtPasswordShown.requestFocus();
            txtPasswordShown.selectEnd();
        }
    }

    private String getPassword() {
        return isPasswordVisible ? txtPasswordShown.getText() : txtPassword.getText();
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = getPassword().trim();

        if (username.isEmpty() || password.isEmpty()) {
            lblMessage.setStyle("-fx-text-fill: #e63946;");
            lblMessage.setText("Please fill in all fields.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM users WHERE username = ? AND password = ? AND status = 'ACTIVE'";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int userId = rs.getInt("user_id");
                String fullName = rs.getString("full_name");
                String role = rs.getString("role");

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/dashboard.fxml"));
                Parent root = loader.load();

                DashboardController dashboardController = loader.getController();
                dashboardController.setUserContext(userId, fullName, role);

                Stage stage = (Stage) btnLogin.getScene().getWindow();
                stage.setScene(new Scene(root, MainApp.APP_WIDTH, MainApp.APP_HEIGHT));
                stage.setResizable(false);
                stage.setTitle("CLAS Equipment Borrowing System - Dashboard");

            } else {
                lblMessage.setStyle("-fx-text-fill: #e63946;");
                lblMessage.setText("Invalid username or password.");
            }

        } catch (SQLException e) {
            lblMessage.setStyle("-fx-text-fill: #e63946;");
            lblMessage.setText("Database Connection Error.");
            e.printStackTrace();
        } catch (IOException e) {
            lblMessage.setStyle("-fx-text-fill: #e63946;");
            lblMessage.setText("Failed to load Dashboard UI.");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Reset Password");
        alert.setHeaderText("Password Recovery");
        alert.setContentText("Please contact your system administrator to reset your account password.");
        alert.showAndWait();
    }
}
