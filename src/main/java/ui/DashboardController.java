package ui;

import db.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DashboardController {

    @FXML private Label lblWelcome;
    @FXML private TableView<Equipment> tblEquipment;
    @FXML private TableColumn<Equipment, Integer> colId;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colStatus;
    @FXML private Button btnLogout;
    @FXML private Button btnBorrow;
    @FXML private Button btnReturn;
    @FXML private Button btnManage;

    private int currentUserId;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("equipmentId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Lock columns in place: can't be dragged to reorder or resized.
        for (TableColumn<Equipment, ?> col : new TableColumn[]{colId, colName, colCategory, colSerial, colStatus}) {
            col.setReorderable(false);
            col.setResizable(false);
        }
        tblEquipment.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        loadEquipmentData();
    }

    public void setUserContext(int userId, String fullName, String role) {
        this.currentUserId = userId;
        lblWelcome.setText("Welcome, " + fullName + " (" + role + ")");

        if (!"ADMIN".equalsIgnoreCase(role) && !"SUPERADMIN".equalsIgnoreCase(role)) {
            btnManage.setDisable(true);
        }
    }

    private void loadEquipmentData() {
        ObservableList<Equipment> data = FXCollections.observableArrayList();
        String sql = "SELECT equipment_id, item_name, category, serial_number, status FROM equipment ORDER BY equipment_id";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                data.add(new Equipment(
                        rs.getInt("equipment_id"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getString("serial_number"),
                        rs.getString("status")
                ));
            }
            tblEquipment.setItems(data);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not load equipment: " + e.getMessage());
        }
    }

    @FXML
    private void handleBorrow(ActionEvent event) {
        Equipment selected = tblEquipment.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an item to borrow.");
            return;
        }
        if (!"AVAILABLE".equalsIgnoreCase(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Unavailable",
                    "\"" + selected.getItemName() + "\" is currently " + selected.getStatus() + " and cannot be borrowed.");
            return;
        }
        if (!confirm("Borrow \"" + selected.getItemName() + "\"?")) return;

        String updateSql = "UPDATE equipment SET status = 'BORROWED' WHERE equipment_id = ?";
        String insertSql = "INSERT INTO borrow_transactions (equipment_id, user_id, borrow_date, due_date, status) " +
                "VALUES (?, ?, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 'BORROWED')";

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setInt(1, selected.getEquipmentId());
                upd.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                ins.setInt(1, selected.getEquipmentId());
                ins.setInt(2, currentUserId);
                ins.executeUpdate();
            }
            loadEquipmentData();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Item borrowed. Due back in 3 days.");
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not complete borrow: " + e.getMessage());
        }
    }

    @FXML
    private void handleReturn(ActionEvent event) {
        Equipment selected = tblEquipment.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an item to return.");
            return;
        }
        if (!"BORROWED".equalsIgnoreCase(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Not Borrowed", "This item is not currently marked as borrowed.");
            return;
        }
        if (!confirm("Return \"" + selected.getItemName() + "\"?")) return;

        String updateEquipSql = "UPDATE equipment SET status = 'AVAILABLE' WHERE equipment_id = ?";
        // MySQL allows ORDER BY + LIMIT directly on UPDATE, so this closes only the most recent open loan.
        String updateTxnSql = "UPDATE borrow_transactions SET return_date = NOW(), status = 'RETURNED' " +
                "WHERE equipment_id = ? AND status = 'BORROWED' ORDER BY transaction_id DESC LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement upd = conn.prepareStatement(updateEquipSql)) {
                upd.setInt(1, selected.getEquipmentId());
                upd.executeUpdate();
            }
            try (PreparedStatement upd = conn.prepareStatement(updateTxnSql)) {
                upd.setInt(1, selected.getEquipmentId());
                upd.executeUpdate();
            }
            loadEquipmentData();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Item returned.");
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not complete return: " + e.getMessage());
        }
    }

    @FXML
    private void handleManage(ActionEvent event) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Manage Inventory");
        dialog.setResizable(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField txtName = new TextField();
        txtName.setPromptText("Item name");
        TextField txtCategory = new TextField();
        txtCategory.setPromptText("Category");
        TextField txtSerial = new TextField();
        txtSerial.setPromptText("Serial number");
        ComboBox<String> cmbStatus = new ComboBox<>(FXCollections.observableArrayList("AVAILABLE", "MAINTENANCE", "LOST"));
        cmbStatus.setValue("AVAILABLE");

        Button btnAdd = new Button("Add Equipment");
        Button btnDelete = new Button("Delete Selected Item");
        Label lblStatus = new Label();

        btnAdd.setOnAction(e -> {
            String name = txtName.getText().trim();
            String category = txtCategory.getText().trim();
            String serial = txtSerial.getText().trim();
            if (name.isEmpty() || category.isEmpty() || serial.isEmpty()) {
                setDialogStatus(lblStatus, "All fields are required.", false);
                return;
            }
            String sql = "INSERT INTO equipment (item_name, category, serial_number, status) VALUES (?, ?, ?, ?)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, category);
                pstmt.setString(3, serial);
                pstmt.setString(4, cmbStatus.getValue());
                pstmt.executeUpdate();
                setDialogStatus(lblStatus, "Item added.", true);
                txtName.clear();
                txtCategory.clear();
                txtSerial.clear();
                loadEquipmentData();
            } catch (SQLException ex) {
                setDialogStatus(lblStatus, "Error: " + ex.getMessage(), false);
            }
        });

        btnDelete.setOnAction(e -> {
            Equipment selected = tblEquipment.getSelectionModel().getSelectedItem();
            if (selected == null) {
                setDialogStatus(lblStatus, "Select an item in the table first.", false);
                return;
            }
            String sql = "DELETE FROM equipment WHERE equipment_id = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, selected.getEquipmentId());
                pstmt.executeUpdate();
                setDialogStatus(lblStatus, "Item deleted.", true);
                loadEquipmentData();
            } catch (SQLException ex) {
                setDialogStatus(lblStatus, "Error: " + ex.getMessage() + " (it may have borrow history)", false);
            }
        });

        grid.addRow(0, new Label("Item Name:"), txtName);
        grid.addRow(1, new Label("Category:"), txtCategory);
        grid.addRow(2, new Label("Serial Number:"), txtSerial);
        grid.addRow(3, new Label("Status:"), cmbStatus);
        grid.add(btnAdd, 1, 4);
        grid.add(btnDelete, 1, 5);
        grid.add(lblStatus, 1, 6);

        dialog.setScene(new Scene(grid, 380, 320));
        dialog.showAndWait();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            Stage stage = (Stage) btnLogout.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/login.fxml"));
            Scene scene = new Scene(loader.load(), MainApp.APP_WIDTH, MainApp.APP_HEIGHT);
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setTitle("CLAS Borrowing System - Login");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load login screen.");
        }
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void setDialogStatus(Label label, String message, boolean success) {
        label.setStyle(success ? "-fx-text-fill: #2ecc71;" : "-fx-text-fill: #e63946;");
        label.setText(message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
