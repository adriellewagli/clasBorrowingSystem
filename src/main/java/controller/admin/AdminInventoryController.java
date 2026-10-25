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
import util.ActionFilter;
import util.LiveRefresh;
import util.TableCells;
import javafx.stage.Window;
import model.ItemFee;
import util.ItemFeeDialog;

import java.util.List;

public class AdminInventoryController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbActionFilter;
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

    @FXML private Button btnEditFees;
    @FXML private Button btnToggleDisable;
    @FXML private Button btnRemove;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<Equipment> inventoryList = FXCollections.observableArrayList();
    private FilteredList<Equipment> filteredList;
    private ActionFilter<Equipment> actionFilter;

    @FXML
    public void initialize() {
        comboCategory.setItems(FXCollections.observableArrayList(
                "Laptops & Computers", "Display & AV", "Audio Equipment", "Photography", "Peripherals"));

        setupTableColumns();
        setupSearch();
        tblInventory.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> updateActionButtons(sel));
        updateActionButtons(null);
        loadInventoryData(); // runs in the background
        // statuses change when requests are approved / items come back: keep the table current
        LiveRefresh.poll(tblInventory, inventoryList, equipmentDAO::getAllEquipment, null);
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
        util.TablePager.install(tblInventory, filteredList, sorted);
        TableCells.bindCount(lblRowCount, filteredList, inventoryList);

        actionFilter = new ActionFilter<>(cmbActionFilter, inventoryList, filteredList, Equipment::getStatus);
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, o, n) -> actionFilter.setSearch(e -> matches(e, n)));
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
        int newId = equipmentDAO.addEquipmentGetId(newEq);

        if (newId > 0) {
            lblFormError.setText("");
            txtName.clear();
            txtSerial.clear();
            comboCategory.getSelectionModel().clearSelection();
            loadInventoryData();
            // straight to the fee screen for the new item
            openFeeEditor(new ItemFee(newId, name, category, serial,
                    ItemFeeDialog.DEFAULT_BORROW_FEE, ItemFeeDialog.DEFAULT_LATE_FEE), true);
        } else {
            lblFormError.setText("Error: Serial number may already exist.");
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadInventoryData();
    }

    private void updateActionButtons(Equipment sel) {
        boolean none = sel == null;
        btnEditFees.setDisable(none);
        btnToggleDisable.setDisable(none);
        btnRemove.setDisable(none);
        btnToggleDisable.setText(!none && sel.isDisabled() ? "Enable" : "Disable");
    }

    private void openFeeEditor(ItemFee item, boolean justCreated) {
        Window owner = tblInventory.getScene() != null ? tblInventory.getScene().getWindow() : null;
        double[] r = ItemFeeDialog.show(owner, item);
        if (r == null) {
            if (justCreated) showSuccessDialog("Equipment Added",
                    item.getEquipmentName() + " was saved with the default fees. You can change them with Edit Fees.");
            return;
        }
        String error = equipmentDAO.updateItemFees(item.getEquipmentId(), r[0], r[1]);
        if (error != null) { showErrorDialog("Could not save the fees", error); return; }
        LiveRefresh.markChanged();
        loadInventoryData();
        showSuccessDialog(justCreated ? "Equipment Added" : "Fees Saved", item.getEquipmentName()
                + String.format(" now costs \u20B1%,.2f to borrow, with a late fee of \u20B1%,.2f per day.", r[0], r[1]));
    }

    @FXML
    private void handleEditFees(ActionEvent event) {
        Equipment sel = tblInventory.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        openFeeEditor(new ItemFee(sel.getId(), sel.getName(), sel.getCategory(), sel.getSerialNumber(),
                sel.getBorrowFee(), sel.getLateFeePerDay()), false);
    }

    @FXML
    private void handleToggleDisable(ActionEvent event) {
        Equipment sel = tblInventory.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        boolean disable = !sel.isDisabled();
        String msg = disable
                ? "Disable " + sel.getName() + "? It stays in the inventory but can't be requested or reserved until you enable it again."
                : "Enable " + sel.getName() + "? Users will be able to request it again.";
        if (!confirm(disable ? "Disable Item" : "Enable Item", msg, disable ? "Disable" : "Enable")) return;
        String error = equipmentDAO.setEquipmentDisabled(sel.getId(), disable);
        if (error != null) { showErrorDialog("Could not update the item", error); return; }
        LiveRefresh.markChanged();
        loadInventoryData();
    }

    @FXML
    private void handleRemove(ActionEvent event) {
        Equipment sel = tblInventory.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!confirm("Remove Item", "Permanently remove " + sel.getName() + " from the inventory? This can't be undone.", "Remove")) return;
        String error = equipmentDAO.removeEquipment(sel.getId());
        if (error != null) { showErrorDialog("Can't remove this item", error); return; }
        LiveRefresh.markChanged();
        loadInventoryData();
        showSuccessDialog("Item Removed", sel.getName() + " was removed from the inventory.");
    }
}