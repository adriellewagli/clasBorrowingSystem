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
import model.Receipt;
import util.ReceiptOverlay;
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
    @FXML private TableColumn<BorrowedItem, Void> colReceipt;

    // Digital receipt pop-up (declared in the FXML)
    @FXML private ReceiptOverlay receiptOverlay;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> auditList = FXCollections.observableArrayList();
    private FilteredList<BorrowedItem> filteredData;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearchFilter();
        loadAuditLogData();
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("borrowerName"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colBorrowDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProcessedBy.setCellValueFactory(new PropertyValueFactory<>("processedBy"));

        colTransId.setCellFactory(TableCells.idCell());
        colBorrower.setCellFactory(TableCells.avatarNameCell());
        colEquipment.setCellFactory(TableCells.primaryCell());
        colBorrowDate.setCellFactory(TableCells.dateCell());
        colDueDate.setCellFactory(TableCells.dateCell());
        colStatus.setCellFactory(TableCells.statusPill());
        colProcessedBy.setCellFactory(TableCells.mutedCell());
        // Pending and rejected requests were never handed out, so there is nothing to receipt yet.
        colReceipt.setCellFactory(TableCells.<BorrowedItem>actionButton(
                "View Receipt", this::openReceipt, this::hasReceipt));

        TableCells.modernize(tblAuditHistory);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    @FXML
    public void loadAuditLogData() {
        auditList.setAll(equipmentDAO.getAllTransactionHistory());
    }

    private void setupSearchFilter() {
        filteredData = new FilteredList<>(auditList, p -> true);
        SortedList<BorrowedItem> sorted = new SortedList<>(filteredData);
        sorted.comparatorProperty().bind(tblAuditHistory.comparatorProperty());
        tblAuditHistory.setItems(sorted);
        TableCells.bindCount(lblRowCount, filteredData, auditList);

        txtSearch.textProperty().addListener((obs, oldV, newV) -> filteredData.setPredicate(item -> {
            if (newV == null || newV.isBlank()) return true;
            String f = newV.toLowerCase().trim();
            return String.valueOf(item.getBorrowId()).contains(f)
                    || has(item.getBorrowerName(), f)
                    || has(item.getEquipmentName(), f)
                    || has(item.getCategory(), f)
                    || has(item.getProcessedBy(), f)
                    || has(item.getStatus(), f);
        }));
    }

    private boolean has(String v, String f) {
        return v != null && v.toLowerCase().contains(f);
    }

    // ------------------------------------------------------------ digital receipt

    private boolean hasReceipt(BorrowedItem item) {
        String st = item.getStatus();
        return !"Pending".equalsIgnoreCase(st) && !"Rejected".equalsIgnoreCase(st);
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
