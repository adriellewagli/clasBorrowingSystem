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
import util.TableCells;

import java.time.LocalDate;
import java.util.List;

public class RequestHistoryLogController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private TableView<BorrowedItem> tblRequestHistory;
    @FXML private TableColumn<BorrowedItem, Integer> colReqId;
    @FXML private TableColumn<BorrowedItem, String> colEquipment;
    @FXML private TableColumn<BorrowedItem, LocalDate> colRequestDate;
    @FXML private TableColumn<BorrowedItem, LocalDate> colTargetReturn;
    @FXML private TableColumn<BorrowedItem, String> colStatus;
    @FXML private TableColumn<BorrowedItem, String> colNotes;

    // Filter chips
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterApproved;
    @FXML private Button btnFilterPending;
    @FXML private Button btnFilterReturned;

    // Optional (add fx:id in the FXML to enable; safe if missing)
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> masterHistoryList = FXCollections.observableArrayList();
    private FilteredList<BorrowedItem> filteredHistory;
    private String statusFilter = null;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFiltering();
        loadHistoryData();
    }

    private void setupTableColumns() {
        colReqId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colRequestDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colTargetReturn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colReqId.setCellFactory(TableCells.idCell());
        colEquipment.setCellFactory(TableCells.primaryCell());
        colRequestDate.setCellFactory(TableCells.dateCell());
        colTargetReturn.setCellFactory(TableCells.dateCell());
        colStatus.setCellFactory(TableCells.statusPill());

        TableCells.modernize(tblRequestHistory);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    private void setupFiltering() {
        filteredHistory = new FilteredList<>(masterHistoryList, i -> true);
        SortedList<BorrowedItem> sorted = new SortedList<>(filteredHistory);
        sorted.comparatorProperty().bind(tblRequestHistory.comparatorProperty());
        tblRequestHistory.setItems(sorted);
        TableCells.bindCount(lblRowCount, filteredHistory, masterHistoryList);

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, o, n) -> applyFilters());
        }
    }

    private void applyFilters() {
        String q = (txtSearch == null || txtSearch.getText() == null) ? "" : txtSearch.getText().trim().toLowerCase();
        filteredHistory.setPredicate(item ->
                (statusFilter == null || statusFilter.equalsIgnoreCase(item.getStatus()))
                        && (q.isEmpty() || has(item.getEquipmentName(), q) || has(item.getStatus(), q)));
    }

    private boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    private void loadHistoryData() {
        UserSession session = UserSession.getInstance();
        int currentUserId = (session != null && session.getUserId() > 0) ? session.getUserId() : 1;

        List<BorrowedItem> history = equipmentDAO.getBorrowHistoryForUser(currentUserId);
        masterHistoryList.setAll(history != null ? history : List.of());
    }

    private void updateActiveFilterChip(Button activeButton) {
        Button[] chips = {btnFilterAll, btnFilterApproved, btnFilterPending, btnFilterReturned};
        for (Button chip : chips) {
            if (chip != null) {
                chip.getStyleClass().remove("filter-chip-active");
                if (!chip.getStyleClass().contains("filter-chip")) chip.getStyleClass().add("filter-chip");
            }
        }
        if (activeButton != null) {
            activeButton.getStyleClass().remove("filter-chip");
            if (!activeButton.getStyleClass().contains("filter-chip-active")) activeButton.getStyleClass().add("filter-chip-active");
        }
    }

    @FXML
    private void filterAll(ActionEvent event) {
        statusFilter = null;
        if (txtSearch != null) txtSearch.clear();
        applyFilters();
        updateActiveFilterChip(btnFilterAll);
    }

    @FXML
    private void filterApproved(ActionEvent event) {
        statusFilter = "Approved";
        applyFilters();
        updateActiveFilterChip(btnFilterApproved);
    }

    @FXML
    private void filterPending(ActionEvent event) {
        statusFilter = "Pending";
        applyFilters();
        updateActiveFilterChip(btnFilterPending);
    }

    @FXML
    private void filterReturned(ActionEvent event) {
        statusFilter = "Returned";
        applyFilters();
        updateActiveFilterChip(btnFilterReturned);
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadHistoryData();
    }
}