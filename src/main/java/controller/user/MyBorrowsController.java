package controller.user;

import controller.BaseController;
import javafx.application.Platform;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.BorrowedItem;
import model.Receipt;
import model.UserSession;
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
    @FXML private TableColumn<BorrowedItem, Void> colReceipt;
    @FXML private TableColumn<BorrowedItem, Void> colAction;

    @FXML private ReceiptOverlay receiptOverlay;

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
        tblActiveBorrows.setItems(activeBorrowsList);
        TableCells.bindCount(lblRowCount, activeBorrowsList, activeBorrowsList);
        loadEquipmentData();
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
        colReceipt.setCellFactory(TableCells.<BorrowedItem>actionButton("View Receipt", this::openReceipt));
        colAction.setCellFactory(TableCells.<BorrowedItem>actionButton(
                "Return item", this::handleReturnRequest,
                (BorrowedItem i) -> !"Pending Return".equalsIgnoreCase(i.getStatus())));

        tblActiveBorrows.getColumns().forEach(col -> col.setReorderable(false));
        TableCells.modernize(tblActiveBorrows);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    private void loadEquipmentData() {
        UserSession session = UserSession.getInstance();
        List<BorrowedItem> dbBorrows = (session != null && session.getUserId() > 0)
                ? equipmentDAO.getActiveBorrowsForUser(session.getUserId())
                : null;
        activeBorrowsList.setAll(dbBorrows != null ? dbBorrows : List.of());
        updateSummaryMetrics();
    }

    private void updateSummaryMetrics() {
        int activeCount = activeBorrowsList.size();
        long overdueCount = activeBorrowsList.stream()
                .filter((BorrowedItem i) -> "Overdue".equalsIgnoreCase(i.getStatus()))
                .count();
        double totalLateFees = overdueCount * 50.00;

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
            showSuccessDialog("Return Requested",
                    "'" + item.getEquipmentName() + "' is now waiting for an admin to check it in.");
            Platform.runLater(() -> {
                loadEquipmentData();
                tblActiveBorrows.refresh();
            });

        } else {

            showErrorDialog("Return Error", "Could not request the return. Please refresh and try again.");
        }
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
        loadEquipmentData();
    }
}
