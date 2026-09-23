package controller;

import db.DatabaseConnection;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Equipment;
import model.UserSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DashboardController extends BaseController {

    @FXML private Label lblUserAvatar;
    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;
    @FXML private Label lblTotalItems;
    @FXML private Label lblAvailableItems;
    @FXML private Label lblBorrowedItems;

    @FXML private TextField txtSearch;
    @FXML private Button btnBorrow;
    @FXML private Button btnReturn;
    @FXML private Button btnAddEquipment;
    @FXML private Button btnManageUsers;

    // Navigation Sidebar Buttons
    @FXML private Button btnNavAdmin;
    @FXML private Button btnNavUsers;

    @FXML private TableView<Equipment> tblEquipment;
    @FXML private TableColumn<Equipment, Integer> colId;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colStatus;

    private ObservableList<Equipment> equipmentList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupUserPermissions();
        setupTableColumns();
        setupColorCoding();
        loadInventoryData();
        setupSearchFilter();
    }

    private void setupUserPermissions() {
        UserSession session = UserSession.getInstance();
        if (session != null) {
            lblUserName.setText(session.getFullName());
            lblUserRole.setText(session.getRole());

            // Dynamic Avatar Initials (e.g. "SA" for Super Admin, "JU" for Juan)
            if (lblUserAvatar != null) {
                lblUserAvatar.setText(getInitials(session.getFullName()));
            }

            String role = session.getRole();
            if ("USER".equalsIgnoreCase(role)) {
                if (btnAddEquipment != null) {
                    btnAddEquipment.setVisible(false);
                    btnAddEquipment.setManaged(false);
                }
                if (btnNavAdmin != null) {
                    btnNavAdmin.setVisible(false);
                    btnNavAdmin.setManaged(false);
                }
                if (btnNavUsers != null) {
                    btnNavUsers.setVisible(false);
                    btnNavUsers.setManaged(false);
                }
            } else if ("ADMIN".equalsIgnoreCase(role)) {
                if (btnNavUsers != null) {
                    btnNavUsers.setVisible(false);
                    btnNavUsers.setManaged(false);
                }
            }
        }
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "US";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    // Color-Coded Table Cells: Green for AVAILABLE, Red for BORROWED, Yellow for REPAIR
    private void setupColorCoding() {
        colStatus.setCellFactory(column -> new TableCell<Equipment, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item.toUpperCase()) {
                        case "AVAILABLE":
                            setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;"); // Green
                            break;
                        case "BORROWED":
                            setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;"); // Red
                            break;
                        case "REPAIR":
                            setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;"); // Yellow
                            break;
                        default:
                            setStyle("");
                            break;
                    }
                }
            }
        });
    }

    private void loadInventoryData() {
        equipmentList.clear();
        int total = 0, available = 0, borrowed = 0;

        String query = "SELECT * FROM equipment";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String status = rs.getString("status");
                equipmentList.add(new Equipment(
                        rs.getInt("equipment_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        status,
                        rs.getString("serial_number")
                ));

                total++;
                if ("AVAILABLE".equalsIgnoreCase(status)) available++;
                else if ("BORROWED".equalsIgnoreCase(status)) borrowed++;
            }

            lblTotalItems.setText(total + " Items");
            lblAvailableItems.setText(available + " Ready");
            lblBorrowedItems.setText(borrowed + " Borrowed");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupSearchFilter() {
        FilteredList<Equipment> filteredData = new FilteredList<>(equipmentList, b -> true);
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(equipment -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();
                return equipment.getName().toLowerCase().contains(lowerCaseFilter)
                        || equipment.getCategory().toLowerCase().contains(lowerCaseFilter)
                        || equipment.getSerialNumber().toLowerCase().contains(lowerCaseFilter);
            });
        });
        tblEquipment.setItems(filteredData);
    }

    @FXML
    private void handleBorrow(ActionEvent event) {
        // Trigger Borrow Transaction Dialog / View
    }

    @FXML
    private void handleReturn(ActionEvent event) {
        // Trigger Return Transaction Dialog / View
    }

    @FXML
    private void handleAddEquipment(ActionEvent event) {
        // Admin / Superadmin action to add new equipment
    }

    @FXML
    private void handleManageUsers(ActionEvent event) {
        // Superadmin action for User Management & Soft Deactivation
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        UserSession.cleanUserSession();
        try {
            navigateTo("/com/borrowclas/clasborrowingsystem/fxml/login.fxml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}