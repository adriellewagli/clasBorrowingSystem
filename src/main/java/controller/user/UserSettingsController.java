package controller.user;

import controller.BaseController;
import db.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import model.UserSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserSettingsController extends BaseController {

    @FXML private TextField txtFullName;
    @FXML private TextField txtUsername;
    @FXML private ComboBox<String> comboDepartment;
    @FXML private Label lblMessage;

    private int userId;

    @FXML
    public void initialize() {
        comboDepartment.setItems(FXCollections.observableArrayList(
                "AB Political Science",
                "BA Communication",
                "Bachelor of Public Administration",
                "Bachelor of Science in Computer Science",
                "Bachelor of Science in Entertainment and Multimedia Computing",
                "Bachelor of Science in Information System",
                "Bachelor of Science in Information Technology",
                "Bachelor of Science in Mathematics",
                "Bachelor of Science in Psychology"
        ));

        UserSession session = UserSession.getInstance();
        if (session != null) {
            userId = session.getUserId();
            loadUserData(userId);
        }
    }

    private void loadUserData(int id) {
        String sql = "SELECT username, full_name, department FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    txtUsername.setText(rs.getString("username"));
                    txtFullName.setText(rs.getString("full_name"));
                    String dept = rs.getString("department");
                    if (dept != null && !dept.isEmpty()) {
                        comboDepartment.setValue(dept);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserSettings Error] Failed to load user profile: " + e.getMessage());
        }
    }

    @FXML
    private void handleSaveProfile(ActionEvent event) {
        String fullName = txtFullName.getText() != null ? txtFullName.getText().trim() : "";
        String department = comboDepartment.getValue() != null ? comboDepartment.getValue().trim() : "";

        if (fullName.isEmpty()) {
            setMessage("Full name cannot be empty.", false);
            return;
        }

        if (department.isEmpty()) {
            setMessage("Please select your academic department/course.", false);
            return;
        }

        String sql = "UPDATE users SET full_name = ?, department = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fullName);
            stmt.setString(2, department);
            stmt.setInt(3, userId);

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                UserSession.getInstance().setFullName(fullName);

                if (txtFullName.getScene() != null && txtFullName.getScene().getUserData() instanceof UserDashboardController) {
                    UserDashboardController dashboard = (UserDashboardController) txtFullName.getScene().getUserData();
                    dashboard.refreshHeaderProfile();
                }

                setMessage("Profile information updated successfully!", true);
                showSuccessDialog("Settings Updated", "Your academic program and profile changes have been saved.");
            } else {
                setMessage("Failed to update profile.", false);
            }
        } catch (SQLException e) {
            System.err.println("[UserSettings Error] Database error: " + e.getMessage());
            setMessage("Database connection error.", false);
        }
    }

    private void setMessage(String text, boolean success) {
        lblMessage.setText(text);
        lblMessage.getStyleClass().removeAll("form-message-error", "form-message-success");
        lblMessage.getStyleClass().add(success ? "form-message-success" : "form-message-error");
    }
}