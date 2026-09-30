package controller.admin;

import controller.BaseController;
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

public class MaintenanceLogController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private TableView<Equipment> tblMaintenance;
    @FXML private TableColumn<Equipment, Integer> colId;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colStatus;
    @FXML private TableColumn<Equipment, Void> colAction;

    @FXML private Label lblMaintenanceCount;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<Equipment> maintenanceList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadMaintenanceData();

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter(newVal));
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
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(status.toUpperCase());
                    badge.getStyleClass().add("status-badge");
                    badge.getStyleClass().add("status-maintenance");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // ACTION BUTTON: MARK AS FIXED
        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnFix = new Button("Mark as Fixed");

            {
                btnFix.getStyleClass().add("btn-primary");
                btnFix.setOnAction(event -> {
                    Equipment item = getTableView().getItems().get(getIndex());
                    handleMarkAsFixed(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnFix);
            }
        });
    }

    private void loadMaintenanceData() {
        maintenanceList.clear();
        // Fetch items flagged as "In Maintenance" or "Repair"
        List<Equipment> dbItems = equipmentDAO.getEquipmentByStatus("In Maintenance");

        // Also fetch any "Repair" status items if applicable
        List<Equipment> repairItems = equipmentDAO.getEquipmentByStatus("Repair");

        maintenanceList.addAll(dbItems);
        for (Equipment eq : repairItems) {
            if (!maintenanceList.contains(eq)) {
                maintenanceList.add(eq);
            }
        }

        if (tblMaintenance != null) {
            tblMaintenance.setItems(maintenanceList);
        }

        if (lblMaintenanceCount != null) {
            lblMaintenanceCount.setText(String.valueOf(maintenanceList.size()));
        }
    }

    private void handleMarkAsFixed(Equipment equipment) {
        boolean success = equipmentDAO.updateStatus(equipment.getId(), "AVAILABLE");
        if (success) {
            showSuccessDialog("Equipment Restored", "'" + equipment.getName() + "' has been marked as fixed and returned to active catalog availability.");
            loadMaintenanceData();
        } else {
            System.err.println("[MaintenanceController Error] Failed to update equipment status.");
        }
    }

    private void applySearchFilter(String query) {
        if (query == null || query.isBlank()) {
            tblMaintenance.setItems(maintenanceList);
            return;
        }

        String lower = query.toLowerCase().trim();
        ObservableList<Equipment> filtered = FXCollections.observableArrayList();
        for (Equipment e : maintenanceList) {
            if (e.getName().toLowerCase().contains(lower) ||
                    e.getCategory().toLowerCase().contains(lower) ||
                    e.getSerialNumber().toLowerCase().contains(lower)) {
                filtered.add(e);
            }
            }
        tblMaintenance.setItems(filtered);
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadMaintenanceData();
    }
}