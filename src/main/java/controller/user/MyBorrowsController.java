package controller.user;

import controller.BaseController;
import javafx.application.Platform;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.BorrowedItem;
import model.Receipt;
import model.UserSession;
import util.ActionFilter;
import util.ReceiptOverlay;
import util.TableCells;

import java.time.LocalDate;
import java.util.List;

public class MyBorrowsController extends BaseController {

    @FXML private TableView<BorrowedItem> tblActiveBorrows;
    @FXML private TableColumn<BorrowedItem, Integer> colBorrowId;
    @FXML private TableColumn<BorrowedItem, String> colEquipmentName;
    @FXML private TableColumn<BorrowedItem, String> colSerialNumber;
    @FXML private TableColumn<BorrowedItem, String> colBorrower;
    @FXML private TableColumn<BorrowedItem, String> colRequestedBy;
    @FXML private TableColumn<BorrowedItem, String> colProcessedBy;
    @FXML private TableColumn<BorrowedItem, LocalDate> colBorrowDate;
    @FXML private TableColumn<BorrowedItem, LocalDate> colDueDate;
    @FXML private TableColumn<BorrowedItem, String> colStatus;
    @FXML private TableColumn<BorrowedItem, String> colFee;
    @FXML private TableColumn<BorrowedItem, String> colPenalty;
    @FXML private ComboBox<String> cmbActionFilter;
    @FXML private TableColumn<BorrowedItem, Void> colReceipt;
    @FXML private TableColumn<BorrowedItem, Void> colAction;

    @FXML private ReceiptOverlay receiptOverlay;

    @FXML private Button btnRefresh;
    @FXML private TextField txtSearch;

    @FXML private Label lblActiveLoansCount;
    @FXML private Label lblOverdueCount;
    @FXML private Label lblTotalLateFees;

    // Optional (add fx:id in my_borrows.fxml to enable; safe if missing)
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> activeBorrowsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        FilteredList<BorrowedItem> filtered = new FilteredList<>(activeBorrowsList, i -> true);
        SortedList<BorrowedItem> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblActiveBorrows.comparatorProperty());
        util.TablePager.install(tblActiveBorrows, filtered, sorted);
        TableCells.bindCount(lblRowCount, filtered, activeBorrowsList);
        ActionFilter<BorrowedItem> actionFilter =
                new ActionFilter<>(cmbActionFilter, activeBorrowsList, filtered, BorrowedItem::getStatus);
        txtSearch.textProperty().addListener((obs, o, n) -> actionFilter.setSearch(item -> {
            if (n == null || n.isBlank()) return true;
            String f = n.toLowerCase().trim();
            return String.valueOf(item.getBorrowId()).contains(f)
                    || has(item.getBorrowerName(), f)
                    || has(item.getEquipmentName(), f)
                    || has(item.getSerialNumber(), f)
                    || has(item.getStatus(), f);
        }));
        loadEquipmentData();
        // an approval (or a return check-in) done by an admin shows up without pressing Refresh
        util.LiveRefresh.poll(tblActiveBorrows, activeBorrowsList, this::fetchBorrows, this::updateSummaryMetrics);
    }

    private static boolean has(String v, String f) {
        return v != null && v.toLowerCase().contains(f);
    }

    private List<BorrowedItem> fetchBorrows() {
        UserSession session = UserSession.getInstance();
        List<BorrowedItem> rows = (session != null && session.getUserId() > 0)
                ? equipmentDAO.getActiveBorrowsForUser(session.getUserId())
                : null;
        return rows != null ? rows : List.of();
    }

    private void setupTableColumns() {
        colBorrowId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colEquipmentName.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colSerialNumber.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("borrowerName"));
        colRequestedBy.setCellValueFactory(new PropertyValueFactory<>("requestedBy"));
        colProcessedBy.setCellValueFactory(new PropertyValueFactory<>("processedBy"));
        colBorrowDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colFee.setCellValueFactory(new PropertyValueFactory<>("feeText"));
        colPenalty.setCellValueFactory(new PropertyValueFactory<>("penaltyText"));

        colBorrowId.setCellFactory(TableCells.idCell());
        colEquipmentName.setCellFactory(TableCells.primaryCell());
        colSerialNumber.setCellFactory(TableCells.monoCell());
        colBorrower.setCellFactory(TableCells.avatarNameCell());
        colRequestedBy.setCellFactory(TableCells.mutedCell());
        colProcessedBy.setCellFactory(TableCells.mutedCell());
        colBorrowDate.setCellFactory(TableCells.dateCell());
        colDueDate.setCellFactory(TableCells.<BorrowedItem, LocalDate>dueDateCell(
                (BorrowedItem i) -> "Overdue".equalsIgnoreCase(i.getStatus())));
        colStatus.setCellFactory(TableCells.statusPill());
        colFee.setCellFactory(TableCells.mutedCell());
        colPenalty.setCellFactory(TableCells.penaltyCell());
        colReceipt.setCellFactory(TableCells.<BorrowedItem>actionButton("View Receipt", this::openReceipt));
        colAction.setCellFactory(TableCells.<BorrowedItem>actionButton(
                "Return item", this::handleReturnRequest,
                (BorrowedItem i) -> !"Pending Return".equalsIgnoreCase(i.getStatus())));

        tblActiveBorrows.getColumns().forEach(col -> col.setReorderable(false));
        TableCells.modernize(tblActiveBorrows);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    private void loadEquipmentData() {
        activeBorrowsList.setAll(fetchBorrows());
        updateSummaryMetrics();
    }

    private void updateSummaryMetrics() {
        int activeCount = activeBorrowsList.size();
        long overdueCount = activeBorrowsList.stream()
                .filter((BorrowedItem i) -> "Overdue".equalsIgnoreCase(i.getStatus()))
                .count();
        // penalty accrues per item: days past that item's own due date x the daily penalty
        double totalLateFees = activeBorrowsList.stream().mapToDouble(BorrowedItem::getLatePenalty).sum();

        if (lblActiveLoansCount != null) lblActiveLoansCount.setText(String.valueOf(activeCount));
        if (lblOverdueCount != null) lblOverdueCount.setText(String.valueOf(overdueCount));
        if (lblTotalLateFees != null) lblTotalLateFees.setText(String.format("₱%.2f", totalLateFees));
    }

    private void handleReturnRequest(BorrowedItem item) {
        if (item == null) return;

        UserSession session = UserSession.getInstance();
        int userId = (session != null && session.getUserId() > 0) ? session.getUserId() : 1;

        // Only flags the item as "Pending Return" - the admin finishes the return in Process Returns.
        boolean success = equipmentDAO.requestReturn(item.getBorrowId(), userId);

        if (success) {
            // any background read that started before this click is now stale - drop it
            util.LiveRefresh.markChanged();

            // show "Pending Return" right away (no waiting for a database round trip)
            int idx = indexOfBorrow(item.getBorrowId());
            if (idx >= 0) {
                BorrowedItem row = activeBorrowsList.get(idx);
                row.setStatus("Pending Return");
                activeBorrowsList.set(idx, row);      // fires a list change so the table redraws
            }
            tblActiveBorrows.refresh();               // re-checks the "Return item" button on every row
            updateSummaryMetrics();

            // then confirm against the database in the background
            util.LiveRefresh.background(this::fetchBorrows, rows -> {
                if (rows != null) {
                    activeBorrowsList.setAll(rows);
                    tblActiveBorrows.refresh();
                    updateSummaryMetrics();
                }
            });
            showSuccessDialog("Return Requested",
                    "'" + item.getEquipmentName() + "' is now waiting for an admin to check it in.");
        } else {

            showErrorDialog("Return Error", "Could not request the return. Please refresh and try again.");
        }
    }

    private int indexOfBorrow(int borrowId) {
        for (int i = 0; i < activeBorrowsList.size(); i++) {
            if (activeBorrowsList.get(i).getBorrowId() == borrowId) return i;
        }
        return -1;
    }

    private void openReceipt(BorrowedItem item) {
        if (item == null) return;
        Receipt r = equipmentDAO.getReceiptForTransaction(item.getBorrowId());
        if (r == null) {
            showErrorDialog("Receipt Unavailable", "Could not load the receipt for transaction #" + item.getBorrowId() + ".");
            return;
        }
        receiptOverlay.show(r);
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        util.LiveRefresh.manualRefresh(btnRefresh, tblActiveBorrows, activeBorrowsList,
                this::fetchBorrows, this::updateSummaryMetrics);
    }
}
