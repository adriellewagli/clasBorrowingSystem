package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.BorrowedItem;
import model.UserSession;
import util.ActionFilter;
import util.ApproveBorrowDialog;
import util.LiveRefresh;
import util.RequestBadge;
import util.TableCells;

import java.time.LocalDate;

public class ManageRequestsController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private Label lblPendingCount;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;
    @FXML private TableView<BorrowedItem> tblRequests;
    @FXML private TableColumn<BorrowedItem, Integer> colTransId;
    @FXML private TableColumn<BorrowedItem, String> colBorrower;
    @FXML private TableColumn<BorrowedItem, String> colEquipment;
    @FXML private TableColumn<BorrowedItem, String> colSerial;
    @FXML private TableColumn<BorrowedItem, String> colType;
    @FXML private ComboBox<String> cmbActionFilter;
    @FXML private TableColumn<BorrowedItem, LocalDate> colBorrowDate;
    @FXML private TableColumn<BorrowedItem, LocalDate> colDueDate;
    @FXML private TableColumn<BorrowedItem, Void> colAction;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> pendingList = FXCollections.observableArrayList();
    private FilteredList<BorrowedItem> filteredData;
    private ActionFilter<BorrowedItem> actionFilter;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearchFilter();
        loadRequests();
        // new requests (and ones handled elsewhere) show up on their own
        LiveRefresh.poll(tblRequests, pendingList, equipmentDAO::getPendingTransactions, this::showPendingCount);
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colBorrower.setCellValueFactory(d -> new SimpleStringProperty(displayBorrower(d.getValue())));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colType.setCellValueFactory(new PropertyValueFactory<>("requestType"));
        colBorrowDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));

        colTransId.setCellFactory(TableCells.idCell());
        colBorrower.setCellFactory(TableCells.avatarNameCell());
        colEquipment.setCellFactory(TableCells.primaryCell());
        colSerial.setCellFactory(TableCells.monoCell());
        colType.setCellFactory(TableCells.statusPill());
        colBorrowDate.setCellFactory(TableCells.dateCell());
        colDueDate.setCellFactory(TableCells.dateCell());
        colAction.setCellFactory(TableCells.<BorrowedItem>dualActionButtons(
                "Approve", this::processApproval, "Reject", this::processRejection));

        TableCells.modernize(tblRequests);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    /** Borrower name if the query provides it, otherwise falls back to category. */
    private static String displayBorrower(BorrowedItem t) {
        String n = t.getBorrowerName();
        return (n != null && !n.isBlank()) ? n : t.getCategory();
    }

    @FXML
    public void loadRequests() {
        pendingList.setAll(equipmentDAO.getPendingTransactions());
        showPendingCount();
    }

    private void showPendingCount() {
        if (lblPendingCount != null) {
            lblPendingCount.setText(String.valueOf(pendingList.size()));
        }
        RequestBadge.PENDING.set(pendingList.size());
    }

    private void processApproval(BorrowedItem item) {
        if (item == null) return;
        javafx.stage.Window owner = tblRequests.getScene() != null ? tblRequests.getScene().getWindow() : null;

        // a reservation is handed over on its pick-up day, not before
        if ("Reservation".equalsIgnoreCase(item.getRequestType())
                && item.getBorrowDate() != null && item.getBorrowDate().isAfter(LocalDate.now())) {
            showErrorDialog("Not yet", "This reservation starts on " + item.getBorrowDate()
                    + ". It can be approved (handed over) from that day.");
            return;
        }

        // "Are you sure?" pop-up - the fees come from the item's settings (Super Admin > Fees & Penalties).
        if (!ApproveBorrowDialog.show(owner, item)) return;

        int adminId = UserSession.getInstance().getUserId();
        String error = equipmentDAO.approveBorrow(item.getBorrowId(), item.getEquipmentId(), adminId);
        loadRequests();
        if (error != null) {
            showErrorDialog("Could not approve", error);
        }
    }

    private void processRejection(BorrowedItem item) {
        if (item == null) return;
        int adminId = UserSession.getInstance().getUserId();
        if (equipmentDAO.rejectBorrowRequest(item.getBorrowId(), item.getEquipmentId(), adminId)) {
            loadRequests();
        }
    }

    private void setupSearchFilter() {
        filteredData = new FilteredList<>(pendingList, p -> true);
        SortedList<BorrowedItem> sorted = new SortedList<>(filteredData);
        sorted.comparatorProperty().bind(tblRequests.comparatorProperty());
        tblRequests.setItems(sorted);
        TableCells.bindCount(lblRowCount, filteredData, pendingList);

        actionFilter = new ActionFilter<>(cmbActionFilter, pendingList, filteredData, BorrowedItem::getRequestType);
        txtSearch.textProperty().addListener((obs, oldV, newV) -> actionFilter.setSearch(item -> {
            if (newV == null || newV.isBlank()) return true;
            String f = newV.toLowerCase().trim();
            return String.valueOf(item.getBorrowId()).contains(f)
                    || has(displayBorrower(item), f)
                    || has(item.getEquipmentName(), f)
                    || has(item.getSerialNumber(), f);
        }));
    }

    private boolean has(String v, String f) {
        return v != null && v.toLowerCase().contains(f);
    }
}