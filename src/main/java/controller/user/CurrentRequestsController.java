package controller.user;

import controller.BaseController;
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
import model.UserSession;
import util.ActionFilter;
import util.TableCells;

import java.time.LocalDate;

/** Staff view of the requests that are still waiting for an admin - each one can be cancelled. */
public class CurrentRequestsController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbActionFilter;
    @FXML private Button btnRefresh;
    @FXML private Label lblPendingCount;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;
    @FXML private TableView<BorrowedItem> tblRequests;
    @FXML private TableColumn<BorrowedItem, Integer> colReqId;
    @FXML private TableColumn<BorrowedItem, String> colEquipment;
    @FXML private TableColumn<BorrowedItem, String> colSerial;
    @FXML private TableColumn<BorrowedItem, String> colType;
    @FXML private TableColumn<BorrowedItem, LocalDate> colStart;
    @FXML private TableColumn<BorrowedItem, LocalDate> colDue;
    @FXML private TableColumn<BorrowedItem, String> colStatus;
    @FXML private TableColumn<BorrowedItem, Void> colAction;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> requests = FXCollections.observableArrayList();
    private FilteredList<BorrowedItem> filtered;
    private ActionFilter<BorrowedItem> actionFilter;

    @FXML
    public void initialize() {
        colReqId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colType.setCellValueFactory(new PropertyValueFactory<>("requestType"));
        colStart.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colDue.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colReqId.setCellFactory(TableCells.idCell());
        colEquipment.setCellFactory(TableCells.primaryCell());
        colSerial.setCellFactory(TableCells.monoCell());
        colType.setCellFactory(TableCells.mutedCell());
        colStart.setCellFactory(TableCells.dateCell());
        colDue.setCellFactory(TableCells.dateCell());
        colStatus.setCellFactory(TableCells.statusPill());
        colAction.setCellFactory(TableCells.<BorrowedItem>dangerButton("Cancel Request", this::cancel));

        TableCells.modernize(tblRequests);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);

        filtered = new FilteredList<>(requests, r -> true);
        SortedList<BorrowedItem> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblRequests.comparatorProperty());
        tblRequests.setItems(sorted);
        TableCells.bindCount(lblRowCount, filtered, requests);

        actionFilter = new ActionFilter<>(cmbActionFilter, requests, filtered, BorrowedItem::getRequestType);
        txtSearch.textProperty().addListener((obs, o, n) -> actionFilter.setSearch(item -> {
            if (n == null || n.isBlank()) return true;
            String q = n.toLowerCase().trim();
            return String.valueOf(item.getBorrowId()).contains(q)
                    || has(item.getEquipmentName(), q) || has(item.getSerialNumber(), q)
                    || has(item.getRequestType(), q) || has(item.getStatus(), q);
        }));

        load();
        // once an admin approves or rejects a request it leaves this list by itself
        util.LiveRefresh.poll(tblRequests, requests, this::fetchRequests, this::showPendingCount);
    }

    /** Database read only - runs off the UI thread. */
    private java.util.List<BorrowedItem> fetchRequests() {
        java.util.List<BorrowedItem> rows = equipmentDAO.getPendingRequestsForUser(currentUserId());
        return rows != null ? rows : java.util.List.of();
    }

    private static int currentUserId() {
        UserSession s = UserSession.getInstance();
        return (s != null && s.getUserId() > 0) ? s.getUserId() : 1;
    }

    private void showPendingCount() {
        if (lblPendingCount != null) lblPendingCount.setText(String.valueOf(requests.size()));
    }

    private static boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    /** Reloads the list in the background; a failed read keeps what is on screen. */
    private void load() {
        util.LiveRefresh.background(this::fetchRequests, rows -> {
            if (rows == null) return;
            requests.setAll(rows);
            tblRequests.refresh();
            showPendingCount();
        });
    }

    private void cancel(BorrowedItem item) {
        if (item == null) return;
        boolean yes = confirm("Cancel this request?",
                "Cancel your " + item.getRequestType().toLowerCase() + " request for '" + item.getEquipmentName()
                        + "'? The admin will no longer see it.", "Yes, cancel it");
        if (!yes) return;

        String failure = equipmentDAO.cancelRequestResult(item.getBorrowId(), currentUserId());
        boolean cancelled = failure == null;

        // a background read that started before this click would bring the row back - drop it
        util.LiveRefresh.markChanged();
        if (cancelled) requests.removeIf(r -> r.getBorrowId() == item.getBorrowId());   // gone immediately
        tblRequests.refresh();
        showPendingCount();
        load();                                                                          // then confirm with the database

        if (cancelled) {
            showSuccessDialog("Request Cancelled", "'" + item.getEquipmentName() + "' was removed from your pending requests.");
        } else {
            showErrorDialog("Could not cancel", failure);
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        util.LiveRefresh.manualRefresh(btnRefresh, tblRequests, requests, this::fetchRequests, this::showPendingCount);
    }
}
