package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.Equipment;
import util.TableCells;

import java.util.List;

public class AdminInventoryController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;
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
    private FilteredList<Equipment> filteredList;

    @FXML
    public void initialize() {
        comboCategory.setItems(FXCollections.observableArrayList(
                "Laptops & Computers", "Display & AV", "Audio Equipment", "Photography", "Peripherals"));

        setupTableColumns();
        setupSearch();
        loadInventoryData(); // runs in the background
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colId.setCellFactory(TableCells.idCell());
        colName.setCellFactory(TableCells.primaryCell());
        colCategory.setCellFactory(TableCells.mutedCell());
        colSerial.setCellFactory(TableCells.monoCell());
        colStatus.setCellFactory(TableCells.statusPill());

        TableCells.modernize(tblInventory);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    private void setupSearch() {
        filteredList = new FilteredList<>(inventoryList, e -> true);
        SortedList<Equipment> sorted = new SortedList<>(filteredList);
        sorted.comparatorProperty().bind(tblInventory.comparatorProperty());
        tblInventory.setItems(sorted);
        TableCells.bindCount(lblRowCount, filteredList, inventoryList);

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, o, n) -> filteredList.setPredicate(e -> matches(e, n)));
        }
    }

    private boolean matches(Equipment e, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        return has(e.getName(), q) || has(e.getCategory(), q) || has(e.getSerialNumber(), q) || has(e.getStatus(), q);
    }

    private boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    private void loadInventoryData() {
        Task<List<Equipment>> dbTask = new Task<>() {
            @Override
            protected List<Equipment> call() {
                return equipmentDAO.getAllEquipment();
            }
        };

        dbTask.setOnSucceeded(e -> inventoryList.setAll(dbTask.getValue()));

        dbTask.setOnFailed(e -> {
            System.err.println("Failed to load inventory from database.");
            if (dbTask.getException() != null) dbTask.getException().printStackTrace();
        });

        Thread bgThread = new Thread(dbTask);
        bgThread.setDaemon(true);
        bgThread.start();
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