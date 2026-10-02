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
    @FXML private TableColumn<BorrowedItem, Void> colAction;

    @FXML private Label lblActiveLoansCount;
    @FXML private Label lblOverdueCount;
    @FXML private Label lblTotalLateFees;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> activeBorrowsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
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

        if (tblActiveBorrows != null) {
            tblActiveBorrows.getColumns().forEach(col -> col.setReorderable(false));
        }

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

                    if (item.equalsIgnoreCase("Active") || item.equalsIgnoreCase("Approved") || item.equalsIgnoreCase("Checked Out")) {
                        badge.getStyleClass().add("status-checked-out");
                    } else if (item.equalsIgnoreCase("Overdue")) {
                        badge.getStyleClass().add("status-maintenance");
                    } else {
                        badge.getStyleClass().add("status-default");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnProcessReturn = new Button("Process Return");

            {
                btnProcessReturn.getStyleClass().add("btn-primary");
                btnProcessReturn.setOnAction(event -> {
                    BorrowedItem item = getTableView().getItems().get(getIndex());
                    handleReturnRequest(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    BorrowedItem currentItem = getTableView().getItems().get(getIndex());
                    if ("Pending Return".equalsIgnoreCase(currentItem.getStatus())) {
                        setGraphic(null);
                    } else {
                        setGraphic(btnProcessReturn);
                    }
                }
            }
        });
    }

    private void loadEquipmentData() {
        activeBorrowsList.clear();

        List<BorrowedItem> dbBorrows = equipmentDAO.getAllActiveTransactions();
        if (dbBorrows != null) {
            activeBorrowsList.addAll(dbBorrows);
        }

        if (tblActiveBorrows != null) {
            tblActiveBorrows.setItems(activeBorrowsList);
            tblActiveBorrows.refresh();
        }

        updateSummaryMetrics();
    }

    private void updateSummaryMetrics() {
        int activeCount = activeBorrowsList.size();
        long overdueCount = activeBorrowsList.stream()
                .filter(i -> i.getStatus() != null && i.getStatus().equalsIgnoreCase("Overdue"))
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

        boolean success = equipmentDAO.processReturnRequest(item.getBorrowId(), 0, userId);

        if (success) {
            showSuccessDialog("Return Processed", "Return for '" + item.getEquipmentName() + "' has been logged successfully.");
            loadEquipmentData();
        } else {
            showErrorDialog("Return Error", "Could not process return. Please try again.");
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadEquipmentData();
    }
}