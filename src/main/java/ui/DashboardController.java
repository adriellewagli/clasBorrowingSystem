package ui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.io.IOException;

public class DashboardController {

    @FXML private Label lblWelcome;
    @FXML private TableView<?> tblEquipment;
    @FXML private Button btnLogout;
    @FXML private Button btnManage;

    public void setUserContext(String fullName, String role) {
        lblWelcome.setText("Welcome, " + fullName + " (" + role + ")");

        // Hide/Disable admin actions if the user isn't an admin
        if (!"ADMIN".equalsIgnoreCase(role) && !"SUPERADMIN".equalsIgnoreCase(role)) {
            btnManage.setDisable(true);
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            Stage stage = (Stage) btnLogout.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/login.fxml"));
            Scene scene = new Scene(loader.load(), 400, 350);
            stage.setScene(scene);
            stage.setTitle("CLAS Borrowing System - Login");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}