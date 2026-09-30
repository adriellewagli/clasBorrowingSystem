package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
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
    @FXML private Button btnNavRequests;
    @FXML private Button btnNavInventory;
    @FXML private Button btnNavMaintenance;
    @FXML private Button btnNavHistory;
    @FXML private StackPane dynamicContentArea;
    @FXML private VBox returnsViewContainer;

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

    private Node requestsView = null;
    private Node inventoryView = null;
    private Node maintenanceView = null;
    private Node historyView = null;

    private boolean isNavigating = false;
    private Button currentActiveButton = null;

    @FXML
    public void initialize() {
        UserSession session = UserSession.getInstance();
        if (session != null) {
            lblUserName.setText(session.getFullName() != null ? session.getFullName() : "Admin");
            lblUserRole.setText("ADMIN");
        }

        currentActiveButton = btnNavReturns;
        comboCondition.setItems(FXCollections.observableArrayList("Good / Undamaged", "Minor Wear", "Damaged (Needs Repair)"));

        setupTableColumns();
        loadTransactionData();
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colBorrower.setCellValueFactory(new PropertyValueFactory<>("category"));
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
        if (tblTransactions != null) {
            tblTransactions.setItems(transactionList);
        }
        updateMetrics();
    }

    private void updateMetrics() {
        if (lblTotalCheckedOut != null) lblTotalCheckedOut.setText(String.valueOf(transactionList.size()));
        long overdue = transactionList.stream().filter(t -> t.getStatus().equals("Overdue")).count();
        if (lblOverdueItems != null) lblOverdueItems.setText(String.valueOf(overdue));
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
            int currentAdminId = UserSession.getInstance().getUserId();

            // Pass currentAdminId so processed_by is updated to the logged-in admin
            boolean success = equipmentDAO.processReturnRequest(selectedTransaction.getBorrowId(), 0, currentAdminId);

            if (success) {
                if (condition != null && condition.contains("Damaged")) {
                    equipmentDAO.updateStatus(selectedTransaction.getBorrowId(), "In Maintenance");
                }
                loadTransactionData();
                closeModal();
            }
        }
    }

    // --- Navigation Actions ---
    @FXML
    private void handleViewReturns(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavReturns) return;

        updateActiveSidebarButton(btnNavReturns);
        if (dynamicContentArea != null && returnsViewContainer != null) {
            switchViewWithLock(returnsViewContainer);
        }
        loadTransactionData();
    }

    @FXML
    private void handleViewRequests(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavRequests) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/admin/manage_requests.fxml"));
            requestsView = loader.load();

            updateActiveSidebarButton(btnNavRequests);
            if (dynamicContentArea != null) {
                switchViewWithLock(requestsView);
            }
        } catch (IOException e) {
            System.err.println("[AdminDashboardController] Error loading manage_requests.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewInventory(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavInventory) return;

        try {
            if (inventoryView == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/admin/admin_inventory.fxml"));
                inventoryView = loader.load();
            }

            updateActiveSidebarButton(btnNavInventory);
            if (dynamicContentArea != null) {
                switchViewWithLock(inventoryView);
            }
        } catch (IOException e) {
            System.err.println("[AdminDashboardController] Error loading admin_inventory.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewMaintenance(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavMaintenance) return;

        try {
            if (maintenanceView == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/admin/maintenance_log.fxml"));
                maintenanceView = loader.load();
            }

            updateActiveSidebarButton(btnNavMaintenance);
            if (dynamicContentArea != null) {
                switchViewWithLock(maintenanceView);
            }
        } catch (IOException e) {
            System.err.println("[AdminDashboardController] Error loading maintenance_log.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewHistory(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavHistory) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/admin/admin_history.fxml"));
            historyView = loader.load();

            updateActiveSidebarButton(btnNavHistory);
            if (dynamicContentArea != null) {
                switchViewWithLock(historyView);
            }
        } catch (IOException e) {
            System.err.println("[AdminDashboardController] Error loading admin_history.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void switchViewWithLock(Node targetView) {
        if (dynamicContentArea == null || targetView == null) return;

        isNavigating = true;
        dynamicContentArea.getChildren().setAll(targetView);

        FadeTransition fade = new FadeTransition(Duration.millis(200), targetView);
        fade.setFromValue(0.3);
        fade.setToValue(1.0);
        fade.setOnFinished(e -> isNavigating = false);
        fade.play();
    }

    private void updateActiveSidebarButton(Button activeButton) {
        currentActiveButton = activeButton;
        Button[] buttons = {btnNavReturns, btnNavRequests, btnNavInventory, btnNavMaintenance, btnNavHistory};
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