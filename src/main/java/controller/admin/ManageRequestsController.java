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
import model.UserSession;

import java.time.LocalDate;
import java.util.List;

public class ManageRequestsController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private TableView<BorrowedItem> tblRequests;
    @FXML private TableColumn<BorrowedItem, Integer> colTransId;
    @FXML private TableColumn<BorrowedItem, String> colBorrower;
    @FXML private TableColumn<BorrowedItem, String> colEquipment;
    @FXML private TableColumn<BorrowedItem, LocalDate> colBorrowDate;
    @FXML private TableColumn<BorrowedItem, LocalDate> colDueDate;
    @FXML private TableColumn<BorrowedItem, String> colStatus;
    @FXML private TableColumn<BorrowedItem, Void> colActions;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> pendingList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        loadPendingRequests();
        setupSearchFilter();
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("category")); // Stores borrower name or dept
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colBorrowDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnApprove = new Button("Approve");
            private final Button btnReject = new Button("Reject");
            private final javafx.scene.layout.HBox container = new javafx.scene.layout.HBox(8, btnApprove, btnReject);

            {
                btnApprove.getStyleClass().add("btn-primary");
                btnReject.getStyleClass().add("btn-danger");

                btnApprove.setOnAction(event -> {
                    BorrowedItem item = getTableView().getItems().get(getIndex());
                    processApproval(item);
                });

                btnReject.setOnAction(event -> {
                    BorrowedItem item = getTableView().getItems().get(getIndex());
                    processRejection(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });
    }

    @FXML
    public void loadPendingRequests() {
        pendingList.clear();
        List<BorrowedItem> dbList = equipmentDAO.getPendingTransactions();
        pendingList.addAll(dbList);
        tblRequests.setItems(pendingList);
    }

    private void processApproval(BorrowedItem item) {
        if (item == null) return;

        int currentAdminId = UserSession.getInstance().getUserId();
        boolean success = equipmentDAO.approveBorrowRequest(item.getBorrowId(), item.getEquipmentId(), currentAdminId);

        if (success) {
            loadPendingRequests();
        }
    }

    private void processRejection(BorrowedItem item) {
        if (item == null) return;

        int currentAdminId = UserSession.getInstance().getUserId();
        boolean success = equipmentDAO.rejectBorrowRequest(item.getBorrowId(), item.getEquipmentId(), currentAdminId);

        if (success) {
            loadPendingRequests();
        }
    }

    private void setupSearchFilter() {
        FilteredList<BorrowedItem> filteredData = new FilteredList<>(pendingList, p -> true);

        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(item -> {
                if (newValue == null || newValue.isBlank()) return true;

                String filter = newValue.toLowerCase().trim();
                if (String.valueOf(item.getBorrowId()).contains(filter)) return true;
                if (item.getEquipmentName() != null && item.getEquipmentName().toLowerCase().contains(filter)) return true;
                return item.getCategory() != null && item.getCategory().toLowerCase().contains(filter);
            });
        });

        tblRequests.setItems(filteredData);
    }
}