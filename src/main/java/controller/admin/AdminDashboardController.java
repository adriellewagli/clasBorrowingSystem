package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import model.BorrowedItem;
import model.UserSession;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class AdminDashboardController extends BaseController {

    @FXML private Label lblUserAvatar;
    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;

    // --- Sidebar & Content FXIDs ---
    @FXML private Button btnNavReturns;
    @FXML private Button btnNavInventory;
    @FXML private Button btnNavMaintenance;
    @FXML private StackPane dynamicContentArea;

    // --- Metrics ---
    @FXML private Label lblTotalCheckedOut;
    @FXML private Label lblPendingReturns;
    @FXML private Label lblOverdueItems;

    // --- Table ---
    @FXML private TableView<BorrowedItem> tblTransactions;
    @FXML private TableColumn<BorrowedItem, Integer> colTransId;
    @FXML private TableColumn<BorrowedItem, String> colBorrower;
    @FXML private TableColumn<BorrowedItem, String> colEquipment;
    @FXML private TableColumn<BorrowedItem, LocalDate> colDueDate;
    @FXML private TableColumn<BorrowedItem, String> colStatus;
    @FXML private TableColumn<BorrowedItem, Void> colAction;

    // --- Modal ---
    @FXML private StackPane modalOverlay;
    @FXML private Label lblModalEquipmentName;
    @FXML private ComboBox<String> comboCondition;
    @FXML private Label lblLateFeeWarning;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> transactionList = FXCollections.observableArrayList();
    private BorrowedItem selectedTransaction;
    private Node inventoryView = null;

    @FXML
    public void initialize() {
        // Setup User Info
        UserSession session = UserSession.getInstance();
        if (session != null) {
            lblUserName.setText(session.getFullName() != null ? session.getFullName() : "Admin");
            lblUserRole.setText("ADMIN");
        }

        // Populate the condition dropdown
        comboCondition.setItems(FXCollections.observableArrayList("Good / Undamaged", "Minor Wear", "Damaged (Needs Repair)"));

        setupTableColumns();
        loadTransactionData();
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("category")); // Using category field to hold borrower name
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colStatus.setCellFactory(column -> new TableCell<BorrowedItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                } else {
                    Label badge = new Label(item.toUpperCase());
                    badge.getStyleClass().add("status-badge");
                    if (item.equalsIgnoreCase("Active")) badge.getStyleClass().add("status-checked-out");
                    else if (item.equalsIgnoreCase("Overdue")) badge.getStyleClass().add("status-maintenance");
                    else badge.getStyleClass().add("status-default");
                    setGraphic(badge);
                }
            }
        });

        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnProcess = new Button("Process Return");
            {
                btnProcess.getStyleClass().add("btn-primary");
                btnProcess.setOnAction(event -> openReturnModal(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnProcess);
            }
        });
    }

    @FXML
    private void loadTransactionData() {
        transactionList.clear();
        List<BorrowedItem> dbList = equipmentDAO.getAllActiveTransactions();
        transactionList.addAll(dbList);
        tblTransactions.setItems(transactionList);
        updateMetrics();
    }

    private void updateMetrics() {
        lblTotalCheckedOut.setText(String.valueOf(transactionList.size()));
        long overdue = transactionList.stream().filter(t -> t.getStatus().equals("Overdue")).count();
        lblOverdueItems.setText(String.valueOf(overdue));
    }

    private void openReturnModal(BorrowedItem transaction) {
        this.selectedTransaction = transaction;
        lblModalEquipmentName.setText(transaction.getEquipmentName());
        comboCondition.getSelectionModel().selectFirst();

        boolean isOverdue = transaction.getStatus().equalsIgnoreCase("Overdue");
        lblLateFeeWarning.setVisible(isOverdue);
        lblLateFeeWarning.setManaged(isOverdue);

        modalOverlay.setVisible(true);
    }

    @FXML
    private void closeModal() {
        modalOverlay.setVisible(false);
        selectedTransaction = null;
    }

    @FXML
    private void confirmReturn() {
        if (selectedTransaction != null) {
            String condition = comboCondition.getValue();

            boolean success = equipmentDAO.processReturnRequest(selectedTransaction.getBorrowId(), 0);

            if (success) {
                if (condition.contains("Damaged")) {
                    // Optional: handle maintenance logging if needed
                }
                loadTransactionData();
                closeModal();
            }
        }
    }

    // --- Navigation Actions ---
    @FXML
    private void handleViewReturns(ActionEvent event) {
        updateActiveSidebarButton(btnNavReturns);
        // Returns view is the default center content container
    }

    @FXML
    private void handleViewInventory(ActionEvent event) {
        updateActiveSidebarButton(btnNavInventory);
        try {
            if (inventoryView == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/admin/admin_inventory.fxml"));
                inventoryView = loader.load();
            }
            if (dynamicContentArea != null) {
                dynamicContentArea.getChildren().setAll(inventoryView);
            }
        } catch (IOException e) {
            System.err.println("[AdminDashboardController] Error loading admin_inventory.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewMaintenance(ActionEvent event) {
        updateActiveSidebarButton(btnNavMaintenance);
        System.out.println("[AdminDashboard] Maintenance Log clicked.");
    }

    private void updateActiveSidebarButton(Button activeButton) {
        Button[] buttons = {btnNavReturns, btnNavInventory, btnNavMaintenance};
        for (Button btn : buttons) {
            if (btn != null) {
                btn.getStyleClass().remove("sidebar-btn-active");
                if (!btn.getStyleClass().contains("sidebar-btn")) {
                    btn.getStyleClass().add("sidebar-btn");
                }
            }
        }
        if (activeButton != null) {
            activeButton.getStyleClass().remove("sidebar-btn");
            if (!activeButton.getStyleClass().contains("sidebar-btn-active")) {
                activeButton.getStyleClass().add("sidebar-btn-active");
            }
        }
    }

    @FXML
    private void handleLogout() throws IOException {
        UserSession.cleanUserSession();
        navigateTo("/com/borrowclas/clasborrowingsystem/fxml/auth/login.fxml");
    }
}