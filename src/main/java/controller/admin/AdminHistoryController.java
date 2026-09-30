package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.BorrowedItem;

import java.time.LocalDate;
import java.util.List;

public class AdminHistoryController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private TableView<BorrowedItem> tblAuditHistory;
    @FXML private TableColumn<BorrowedItem, Integer> colTransId;
    @FXML private TableColumn<BorrowedItem, String> colBorrower;
    @FXML private TableColumn<BorrowedItem, String> colEquipment;
    @FXML private TableColumn<BorrowedItem, LocalDate> colBorrowDate;
    @FXML private TableColumn<BorrowedItem, LocalDate> colDueDate;
    @FXML private TableColumn<BorrowedItem, String> colStatus;
    @FXML private TableColumn<BorrowedItem, String> colProcessedBy;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> auditList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadAuditLogData();
        setupSearchFilter();
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("category")); // Maps to borrower_name or category
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colBorrowDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colProcessedBy.setCellValueFactory(new PropertyValueFactory<>("processedBy"));

        // Custom status badge rendering
        colStatus.setCellFactory(column -> new TableCell<BorrowedItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                } else {
                    Label badge = new Label(item.toUpperCase());
                    badge.getStyleClass().add("status-badge");
                    if (item.equalsIgnoreCase("Active") || item.equalsIgnoreCase("Approved")) badge.getStyleClass().add("status-checked-out");
                    else if (item.equalsIgnoreCase("Returned")) badge.getStyleClass().add("status-default");
                    else if (item.equalsIgnoreCase("Overdue") || item.equalsIgnoreCase("Rejected")) badge.getStyleClass().add("status-maintenance");
                    else badge.getStyleClass().add("status-default");
                    setGraphic(badge);
                }
            }
        });
    }

    @FXML
    public void loadAuditLogData() {
        auditList.clear();
        List<BorrowedItem> dbList = equipmentDAO.getAllTransactionHistory();
        auditList.addAll(dbList);
        tblAuditHistory.setItems(auditList);
    }

    private void setupSearchFilter() {
        FilteredList<BorrowedItem> filteredData = new FilteredList<>(auditList, p -> true);

        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(item -> {
                if (newValue == null || newValue.isBlank()) return true;

                String filter = newValue.toLowerCase().trim();
                if (String.valueOf(item.getBorrowId()).contains(filter)) return true;
                if (item.getEquipmentName() != null && item.getEquipmentName().toLowerCase().contains(filter)) return true;
                if (item.getCategory() != null && item.getCategory().toLowerCase().contains(filter)) return true;
                if (item.getProcessedBy() != null && item.getProcessedBy().toLowerCase().contains(filter)) return true;
                return item.getStatus() != null && item.getStatus().toLowerCase().contains(filter);
            });
        });

        tblAuditHistory.setItems(filteredData);
    }
}