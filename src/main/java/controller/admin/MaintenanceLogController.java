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
import util.ActionFilter;
import util.DamageReportView;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.layout.VBox;
import util.LiveRefresh;
import util.TableCells;

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
    @FXML private TableColumn<Equipment, Void> colReport;
    @FXML private ComboBox<String> cmbActionFilter;

    @FXML private Label lblMaintenanceCount;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<Equipment> maintenanceList = FXCollections.observableArrayList();
    private FilteredList<Equipment> filteredList;
    private ActionFilter<Equipment> actionFilter;

    @FXML
    public void initialize() {
        setupTableColumns();

        filteredList = new FilteredList<>(maintenanceList, e -> true);
        SortedList<Equipment> sorted = new SortedList<>(filteredList);
        sorted.comparatorProperty().bind(tblMaintenance.comparatorProperty());
        util.TablePager.install(tblMaintenance, filteredList, sorted);
        TableCells.bindCount(lblRowCount, filteredList, maintenanceList);
        actionFilter = new ActionFilter<>(cmbActionFilter, maintenanceList, filteredList, Equipment::getStatus);

        loadMaintenanceData();
        // items sent here from Process Return (and fixes made elsewhere) appear without a manual refresh
        LiveRefresh.poll(tblMaintenance, maintenanceList, this::fetchMaintenance, this::showCount);

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

        // same renderers as the other admin tables, so columns size themselves to fit their text
        colId.setCellFactory(TableCells.idCell());
        colName.setCellFactory(TableCells.primaryCell());
        colCategory.setCellFactory(TableCells.mutedCell());
        colSerial.setCellFactory(TableCells.monoCell());
        colStatus.setCellFactory(TableCells.statusPill());

        // VIEW THE NARRATIVE DAMAGE REPORT WRITTEN WHEN THE ITEM CAME BACK
        colReport.setCellFactory(TableCells.<Equipment>actionButton("View Report", this::viewReport));

        // ACTION BUTTON: MARK AS FIXED
        colAction.setCellFactory(TableCells.<Equipment>actionButton("Mark as Fixed", this::handleMarkAsFixed));

        TableCells.modernize(tblMaintenance);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    private void viewReport(Equipment item) {
        if (item == null) return;
        DamageReportView.show(tblMaintenance.getScene().getWindow(), item.getName(),
                equipmentDAO.getLatestDamageReport(item.getId()));
    }

    /** Reads the items that are in maintenance / repair (database only - safe to call off the UI thread). */
    private List<Equipment> fetchMaintenance() {
        List<Equipment> rows = new java.util.ArrayList<>(equipmentDAO.getEquipmentByStatus("In Maintenance"));
        // Also fetch any "Repair" status items if applicable
        rows.addAll(equipmentDAO.getEquipmentByStatus("Repair"));
        return rows;
    }

    private void loadMaintenanceData() {
        maintenanceList.setAll(fetchMaintenance());
        showCount();
    }

    private void showCount() {
        if (lblMaintenanceCount != null) {
            lblMaintenanceCount.setText(String.valueOf(maintenanceList.size()));
        }
    }

    private void handleMarkAsFixed(Equipment equipment) {
        boolean success = equipmentDAO.updateStatus(equipment.getId(), "AVAILABLE");
        if (success) {
            loadMaintenanceData(); // update the table first, so it is already current behind the pop-up
            showSuccessDialog("Equipment Restored", "'" + equipment.getName() + "' has been marked as fixed and returned to active catalog availability.");
        } else {
            System.err.println("[MaintenanceController Error] Failed to update equipment status.");
        }
    }

    private void applySearchFilter(String query) {
        if (query == null || query.isBlank()) {
            actionFilter.setSearch(null);
            return;
        }

        String lower = query.toLowerCase().trim();
        actionFilter.setSearch(e ->
                e.getName().toLowerCase().contains(lower) ||
                e.getCategory().toLowerCase().contains(lower) ||
                e.getSerialNumber().toLowerCase().contains(lower));
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadMaintenanceData();
    }
}