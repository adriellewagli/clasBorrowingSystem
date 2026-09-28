package controller.admin;

import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Equipment;

import java.util.List;

public class AdminInventoryController {

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
        loadInventoryData();

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
        inventoryList.clear();
        List<Equipment> dbList = equipmentDAO.getAllEquipment();
        inventoryList.addAll(dbList);
        tblInventory.setItems(inventoryList);
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

        // Create new equipment object (Status defaults to AVAILABLE in DAO)
        Equipment newEq = new Equipment(name, category, serial, "AVAILABLE");
        boolean success = equipmentDAO.addEquipment(newEq);

        if (success) {
            lblFormError.setText("");
            txtName.clear();
            txtSerial.clear();
            comboCategory.getSelectionModel().clearSelection();
            loadInventoryData();

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Equipment added successfully!", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        } else {
            lblFormError.setText("Error: Serial number may already exist.");
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadInventoryData();
    }
}