package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Equipment;

import java.util.List;

public class AdminInventoryController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private TableView<Equipment> tblInventory;
    @FXML private TableColumn<Equipment, Integer> colId;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colStatus;

    // Form fields
    @FXML private TextField txtName;
    @FXML private ComboBox<String> comboCategory;
    @FXML private TextField txtSerial;
    @FXML private Label lblFormError;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<Equipment> inventoryList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadInventoryData(); // Now runs seamlessly in the background

        // Populate category dropdown programmatically
        comboCategory.setItems(FXCollections.observableArrayList(
                "Laptops & Computers",
                "Display & AV",
                "Audio Equipment",
                "Photography",
                "Peripherals"
        ));

        // Live search filter listener
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(newVal));
        }
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colStatus.setCellFactory(column -> new TableCell<Equipment, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null); setGraphic(null);
                } else {
                    Label badge = new Label(status.toUpperCase());
                    badge.getStyleClass().add("status-badge");
                    if (status.equalsIgnoreCase("Available")) badge.getStyleClass().add("status-available");
                    else if (status.equalsIgnoreCase("Borrowed") || status.equalsIgnoreCase("Checked Out")) badge.getStyleClass().add("status-checked-out");
                    else badge.getStyleClass().add("status-maintenance");
                    setGraphic(badge);
                }
            }
        });
    }

    private void loadInventoryData() {
        // Offload database work to a background thread so the UI dots keep bouncing
        Task<List<Equipment>> dbTask = new Task<>() {
            @Override
            protected List<Equipment> call() {
                return equipmentDAO.getAllEquipment();
            }
        };

        // When DB finishes, safely update the table on the main UI thread
        dbTask.setOnSucceeded(e -> {
            inventoryList.clear();
            inventoryList.addAll(dbTask.getValue());
            tblInventory.setItems(inventoryList);
        });

        dbTask.setOnFailed(e -> {
            System.err.println("Failed to load inventory from database.");
            if (dbTask.getException() != null) {
                dbTask.getException().printStackTrace();
            }
        });

        // Start the background process
        Thread bgThread = new Thread(dbTask);
        bgThread.setDaemon(true);
        bgThread.start();
    }

    private void applyFilter(String query) {
        if (query == null || query.isBlank()) {
            tblInventory.setItems(inventoryList);
            return;
        }
        String lower = query.toLowerCase().trim();
        ObservableList<Equipment> filtered = FXCollections.observableArrayList();
        for (Equipment e : inventoryList) {
            if (e.getName().toLowerCase().contains(lower) ||
                    e.getCategory().toLowerCase().contains(lower) ||
                    e.getSerialNumber().toLowerCase().contains(lower)) {
                filtered.add(e);
            }
        }
        tblInventory.setItems(filtered);
    }

    @FXML
    private void handleSaveEquipment(ActionEvent event) {
        String name = txtName.getText() != null ? txtName.getText().trim() : "";
        String category = comboCategory.getValue();
        String serial = txtSerial.getText() != null ? txtSerial.getText().trim() : "";

        if (name.isEmpty() || category == null || serial.isEmpty()) {
            lblFormError.setText("Please fill out all fields.");
            return;
        }

        Equipment newEq = new Equipment(name, category, serial, "AVAILABLE");
        boolean success = equipmentDAO.addEquipment(newEq);

        if (success) {
            lblFormError.setText("");
            txtName.clear();
            txtSerial.clear();
            comboCategory.getSelectionModel().clearSelection();

            // Reload the table data asynchronously
            loadInventoryData();

            showSuccessDialog("Equipment Added", "The new item has been successfully saved to the inventory database.");
        } else {
            lblFormError.setText("Error: Serial number may already exist.");
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadInventoryData();
    }
}