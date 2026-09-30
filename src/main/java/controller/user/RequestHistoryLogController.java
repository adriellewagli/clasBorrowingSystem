package controller.user;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.BorrowedItem;
import model.UserSession;

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

    // Filter Chips
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterApproved;
    @FXML private Button btnFilterPending;
    @FXML private Button btnFilterReturned;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> masterHistoryList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadHistoryData();

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter(newVal));
        }
    }

    private void setupTableColumns() {
        colReqId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colRequestDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colTargetReturn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Status badge color coding matching system palette
        colStatus.setCellFactory(column -> new TableCell<BorrowedItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item.toUpperCase());
                    badge.getStyleClass().add("status-badge");

                    switch (item.toLowerCase()) {
                        case "approved":
                        case "active":
                            badge.getStyleClass().add("status-available");
                            break;
                        case "pending":
                        case "pending return":
                            badge.getStyleClass().add("status-checked-out");
                            break;
                        case "rejected":
                        case "overdue":
                            badge.getStyleClass().add("status-maintenance");
                            break;
                        default:
                            badge.getStyleClass().add("status-default");
                            break;
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
    }

    private void loadHistoryData() {
        masterHistoryList.clear();
        int currentUserId = 1; // Fallback or UserSession.getInstance().getUserId()

        List<BorrowedItem> history = equipmentDAO.getBorrowHistoryForUser(currentUserId);
        if (history != null) {
            masterHistoryList.addAll(history);
        }

        if (tblRequestHistory != null) {
            tblRequestHistory.setItems(masterHistoryList);
        }
    }

    private void applySearchFilter(String query) {
        if (query == null || query.isBlank()) {
            tblRequestHistory.setItems(masterHistoryList);
            return;
        }

        String lower = query.toLowerCase().trim();
        ObservableList<BorrowedItem> filtered = FXCollections.observableArrayList();

        for (BorrowedItem item : masterHistoryList) {
            if (item.getEquipmentName().toLowerCase().contains(lower) ||
                    item.getStatus().toLowerCase().contains(lower)) {
                filtered.add(item);
            }
        }
        tblRequestHistory.setItems(filtered);
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
        updateActiveFilterChip(btnFilterAll);
        if (txtSearch != null) txtSearch.clear();
        tblRequestHistory.setItems(masterHistoryList);
    }

    @FXML
    private void filterApproved(ActionEvent event) {
        updateActiveFilterChip(btnFilterApproved);
        filterByStatus("Approved");
    }

    @FXML
    private void filterPending(ActionEvent event) {
        updateActiveFilterChip(btnFilterPending);
        filterByStatus("Pending");
    }

    @FXML
    private void filterReturned(ActionEvent event) {
        updateActiveFilterChip(btnFilterReturned);
        filterByStatus("Returned");
    }

    private void filterByStatus(String status) {
        ObservableList<BorrowedItem> filtered = FXCollections.observableArrayList();
        for (BorrowedItem item : masterHistoryList) {
            if (status.equalsIgnoreCase(item.getStatus())) {
                filtered.add(item);
            }
        }
        tblRequestHistory.setItems(filtered);
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadHistoryData();
    }
}