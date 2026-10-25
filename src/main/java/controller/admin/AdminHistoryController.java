package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.BorrowedItem;
import model.BorrowerDetails;
import model.Receipt;
import model.UserSession;
import javafx.event.ActionEvent;
import util.BorrowerDetailsDialog;
import util.ReceiptOverlay;
import util.StatusGroups;
import util.TableCells;

import java.time.LocalDate;

public class AdminHistoryController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;
    @FXML private TableView<BorrowedItem> tblAuditHistory;
    @FXML private TableColumn<BorrowedItem, Integer> colTransId;
    @FXML private TableColumn<BorrowedItem, String> colBorrower;
    @FXML private TableColumn<BorrowedItem, String> colEquipment;
    @FXML private TableColumn<BorrowedItem, LocalDate> colBorrowDate;
    @FXML private TableColumn<BorrowedItem, LocalDate> colDueDate;
    @FXML private TableColumn<BorrowedItem, String> colStatus;
    @FXML private TableColumn<BorrowedItem, String> colProcessedBy;
    @FXML private TableColumn<BorrowedItem, String> colFee;
    @FXML private TableColumn<BorrowedItem, String> colPenalty;

    // Filter chips: what is currently pending, active, rejected ...
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterPending;
    @FXML private Button btnFilterActive;
    @FXML private Button btnFilterRejected;
    @FXML private Button btnFilterReturned;
    @FXML private Button btnFilterCancelled;
    @FXML private TableColumn<BorrowedItem, Void> colReceipt;
    // "Borrower info" column: who borrowed it + ID photo. Super Admin only.
    @FXML private TableColumn<BorrowedItem, Void> colBorrowerInfo;

    // Digital receipt pop-up (declared in the FXML)
    @FXML private ReceiptOverlay receiptOverlay;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> auditList = FXCollections.observableArrayList();
    private FilteredList<BorrowedItem> filteredData;
    private String statusFilter = null;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearchFilter();
        loadAuditLogData();
        util.LiveRefresh.poll(tblAuditHistory, auditList, equipmentDAO::getAllTransactionHistory, null);
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("borrowerName"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colBorrowDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProcessedBy.setCellValueFactory(new PropertyValueFactory<>("processedBy"));
        colFee.setCellValueFactory(new PropertyValueFactory<>("feeText"));
        colPenalty.setCellValueFactory(new PropertyValueFactory<>("penaltyText"));

        colTransId.setCellFactory(TableCells.idCell());
        colBorrower.setCellFactory(TableCells.avatarNameCell());
        colEquipment.setCellFactory(TableCells.primaryCell());
        colBorrowDate.setCellFactory(TableCells.dateCell());
        colDueDate.setCellFactory(TableCells.dateCell());
        colStatus.setCellFactory(TableCells.statusPill());
        colProcessedBy.setCellFactory(TableCells.mutedCell());
        colFee.setCellFactory(TableCells.mutedCell());
        colPenalty.setCellFactory(TableCells.penaltyCell());
        // Pending and rejected requests were never handed out, so there is nothing to receipt yet.
        colReceipt.setCellFactory(TableCells.<BorrowedItem>actionButton(
                "View Receipt", this::openReceipt, this::hasReceipt));

        // Who borrowed it and their ID photo: Super Admin only. For everyone else the column is removed.
               if (isSuperAdmin()) {
                        colBorrowerInfo.setCellFactory(TableCells.<BorrowedItem>actionButton("View details", this::openBorrowerDetails));
                   } else {
                        tblAuditHistory.getColumns().remove(colBorrowerInfo);
                    }

        TableCells.modernize(tblAuditHistory);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    private static boolean isSuperAdmin() {
                UserSession session = UserSession.getInstance();
                return session != null && session.isSuperAdmin();
            }

    @FXML
    public void loadAuditLogData() {
        auditList.setAll(equipmentDAO.getAllTransactionHistory());
    }

    private void setupSearchFilter() {
        filteredData = new FilteredList<>(auditList, p -> true);
        SortedList<BorrowedItem> sorted = new SortedList<>(filteredData);
        sorted.comparatorProperty().bind(tblAuditHistory.comparatorProperty());
        util.TablePager.install(tblAuditHistory, filteredData, sorted);
        TableCells.bindCount(lblRowCount, filteredData, auditList);

        txtSearch.textProperty().addListener((obs, oldV, newV) -> applyFilters());
    }

    /** Search box and status chip work together. */
    private void applyFilters() {
        String f = txtSearch.getText() == null ? "" : txtSearch.getText().toLowerCase().trim();
        filteredData.setPredicate(item ->
                StatusGroups.matches(statusFilter, item.getStatus())
                        && (f.isEmpty()
                        || String.valueOf(item.getBorrowId()).contains(f)
                        || has(item.getBorrowerName(), f)
                        || has(item.getEquipmentName(), f)
                        || has(item.getCategory(), f)
                        || has(item.getProcessedBy(), f)
                        || has(item.getStatus(), f)));
    }

    private void setChip(String group, Button active) {
        statusFilter = group;
        applyFilters();
        Button[] chips = {btnFilterAll, btnFilterPending, btnFilterActive, btnFilterRejected, btnFilterReturned, btnFilterCancelled};
        for (Button chip : chips) {
            if (chip == null) continue;
            chip.getStyleClass().remove("filter-chip-active");
            if (!chip.getStyleClass().contains("filter-chip")) chip.getStyleClass().add("filter-chip");
        }
        if (active != null) {
            active.getStyleClass().remove("filter-chip");
            active.getStyleClass().add("filter-chip-active");
        }
    }

    @FXML private void filterAll(ActionEvent e)       { setChip(null, btnFilterAll); }
    @FXML private void filterPending(ActionEvent e)   { setChip("Pending", btnFilterPending); }
    @FXML private void filterActive(ActionEvent e)    { setChip("Active", btnFilterActive); }
    @FXML private void filterRejected(ActionEvent e)  { setChip("Rejected", btnFilterRejected); }
    @FXML private void filterReturned(ActionEvent e)  { setChip("Returned", btnFilterReturned); }
    @FXML private void filterCancelled(ActionEvent e) { setChip("Cancelled", btnFilterCancelled); }

    private boolean has(String v, String f) {
        return v != null && v.toLowerCase().contains(f);
    }

    // ------------------------------------------------------------ digital receipt

    private boolean hasReceipt(BorrowedItem item) {
        String st = item.getStatus();
        return !"Pending".equalsIgnoreCase(st) && !"Rejected".equalsIgnoreCase(st)
                && !"Cancelled".equalsIgnoreCase(st) && !"Reserved".equalsIgnoreCase(st);

    }
    // ------------------------------------------------------------ borrower details + ID photo (Super Admin)

    private void openBorrowerDetails(BorrowedItem item) {
        if (item == null || !isSuperAdmin()) return;   // checked again here, not only by hiding the column

        BorrowerDetails details = equipmentDAO.getBorrowerDetails(item.getBorrowId());
        if (details == null) {
            showErrorDialog("Details Unavailable", "Could not load the borrower details for transaction #" + item.getBorrowId() + ".");
            return;
        }
        javafx.stage.Window owner = tblAuditHistory.getScene() != null ? tblAuditHistory.getScene().getWindow() : null;
        BorrowerDetailsDialog.show(owner, item, details);
    }

    private void openReceipt(BorrowedItem item) {
        Receipt r = equipmentDAO.getReceiptForTransaction(item.getBorrowId());
        if (r == null) {
            showErrorDialog("Receipt Unavailable", "Could not load the receipt for transaction #" + item.getBorrowId() + ".");
            return;
        }
        receiptOverlay.show(r);
    }
}
