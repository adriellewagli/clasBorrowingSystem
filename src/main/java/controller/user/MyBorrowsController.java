package controller.user;

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

public class MyBorrowsController {

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

    // --- DATABASE DAO ---
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

        // STATUS BADGE CELL FACTORY (Matching your exact CSS rules)
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
                        badge.getStyleClass().add("status-maintenance"); // Uses amber/orange highlight for warning
                    } else {
                        badge.getStyleClass().add("status-default");
                    }
                    setGraphic(badge);
                }
            }
        });

        // RETURN ACTION BUTTON
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
                    // Hide the return button if the status is already "Pending Return"
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

        // Get the active User's ID (fallback to 1 if session isn't fully integrated yet)
        UserSession session = UserSession.getInstance();
        int currentUserId = 1;
        // if (session != null && session.getUserId() > 0) currentUserId = session.getUserId();

        // FETCH DYNAMICALLY FROM MARIADB DATABASE
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

        // Base fee of 50 per overdue item.
        double totalLateFees = overdueCount * 50.00;

        if (lblActiveLoansCount != null) lblActiveLoansCount.setText(String.valueOf(activeCount));
        if (lblOverdueCount != null) lblOverdueCount.setText(String.valueOf(overdueCount));
        if (lblTotalLateFees != null) lblTotalLateFees.setText(String.format("₱%.2f", totalLateFees));
    }

    private void handleReturnRequest(BorrowedItem item) {
        // UI Action feedback trigger.
        // We only update the UI state here. The actual physical return
        // is processed in the Admin dashboard via DAO.processReturnRequest().
        item.setStatus("Pending Return");
        tblActiveBorrows.refresh();
        updateSummaryMetrics();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Return Requested");
        alert.setHeaderText(null);
        alert.setContentText("Return request for '" + item.getEquipmentName() + "' submitted. Please present the physical item to the equipment custodian.");
        alert.showAndWait();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadEquipmentData();
    }
}