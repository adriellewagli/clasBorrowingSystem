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

// EXTENDS BASECONTROLLER NOW
public class MyBorrowsController extends BaseController {

    @FXML private TableView<BorrowedItem> tblActiveBorrows;
    @FXML private TableColumn<BorrowedItem, Integer> colBorrowId;
    @FXML private TableColumn<BorrowedItem, String> colEquipmentName;
    @FXML private TableColumn<BorrowedItem, String> colSerialNumber;
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
        colBorrowDate.setCellValueFactory(new PropertyValueFactory<>("borrowDate"));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

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

                    if (item.equalsIgnoreCase("Active")) {
                        badge.getStyleClass().add("status-checked-out");
                    } else if (item.equalsIgnoreCase("Overdue")) {
                        badge.getStyleClass().add("status-maintenance");
                    } else {
                        badge.getStyleClass().add("status-default");
                    }
                    setGraphic(badge);
                }
            }
        });

        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnReturn = new Button("Return");

            {
                btnReturn.getStyleClass().add("btn-primary");
                btnReturn.setOnAction(event -> {
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
                    if ("Pending Return".equals(getTableView().getItems().get(getIndex()).getStatus())) {
                        setGraphic(null);
                    } else {
                        setGraphic(btnReturn);
                    }
                }
            }
        });
    }

    private void loadEquipmentData() {
        activeBorrowsList.clear();
        int currentUserId = 1;

        List<BorrowedItem> dbBorrows = equipmentDAO.getActiveBorrowsForUser(currentUserId);
        activeBorrowsList.addAll(dbBorrows);

        if (tblActiveBorrows != null) {
            tblActiveBorrows.setItems(activeBorrowsList);
        }

        updateSummaryMetrics();
    }

    private void updateSummaryMetrics() {
        int activeCount = activeBorrowsList.size();
        long overdueCount = activeBorrowsList.stream()
                .filter(i -> i.getStatus().equalsIgnoreCase("Overdue"))
                .count();

        double totalLateFees = overdueCount * 50.00;

        if (lblActiveLoansCount != null) lblActiveLoansCount.setText(String.valueOf(activeCount));
        if (lblOverdueCount != null) lblOverdueCount.setText(String.valueOf(overdueCount));
        if (lblTotalLateFees != null) lblTotalLateFees.setText(String.format("₱%.2f", totalLateFees));
    }

    private void handleReturnRequest(BorrowedItem item) {
        item.setStatus("Pending Return");
        tblActiveBorrows.refresh();
        updateSummaryMetrics();

        // REPLACED CLUNKY ALERT WITH SLEEK CUSTOM DIALOG
        showSuccessDialog("Return Initiated", "Request for '" + item.getEquipmentName() + "' submitted. Please present the item to the admin desk.");
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadEquipmentData();
    }
}