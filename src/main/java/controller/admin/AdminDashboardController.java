package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.BorrowedItem;
import model.UserSession;
import util.CredentialsDialog;
import util.TableCells;

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
    @FXML private Button btnNavAccounts;
    @FXML private StackPane dynamicContentArea;
    @FXML private VBox returnsViewContainer;

    // --- Metrics ---
    @FXML private Label lblTotalCheckedOut;
    @FXML private Label lblPendingReturns;
    @FXML private Label lblOverdueItems;

    // --- Search + Table ---
    @FXML private TextField txtSearch;
    @FXML private VBox tableCard;
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
    private FilteredList<BorrowedItem> filteredList;
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
            boolean superAdmin = session.isSuperAdmin();
            lblUserRole.setText(superAdmin ? "SUPER ADMIN" : "ADMIN");
            // Only the Super Admin sees (and can open) the account management tab.
            if (btnNavAccounts != null) {
                btnNavAccounts.setVisible(superAdmin);
                btnNavAccounts.setManaged(superAdmin);
            }
        }

        currentActiveButton = btnNavReturns;
        comboCondition.setItems(FXCollections.observableArrayList("Good / Undamaged", "Minor Wear", "Damaged (Needs Repair)"));

        setupTableColumns();
        setupSearch();
        loadTransactionData();
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colBorrower.setCellValueFactory(d -> new SimpleStringProperty(displayBorrower(d.getValue())));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // --- Modern cell renderers (see util/TableCells.java) ---
        colTransId.setCellFactory(TableCells.<BorrowedItem, Integer>idCell());
        colBorrower.setCellFactory(TableCells.avatarNameCell());
        colEquipment.setCellFactory(TableCells.primaryCell());
        colDueDate.setCellFactory(TableCells.<BorrowedItem, LocalDate>dueDateCell(
                t -> "Overdue".equalsIgnoreCase(t.getStatus())));
        colStatus.setCellFactory(TableCells.<BorrowedItem>statusPill());
        colAction.setCellFactory(TableCells.<BorrowedItem>actionButton("Process Return", this::openReturnModal));

        TableCells.modernize(tblTransactions);
        // Round the table corners to match the card
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    /** Wires the search box: filters by ID, borrower, equipment, or status. */
    private void setupSearch() {
        filteredList = new FilteredList<>(transactionList, t -> true);
        SortedList<BorrowedItem> sorted = new SortedList<>(filteredList);
        sorted.comparatorProperty().bind(tblTransactions.comparatorProperty());
        tblTransactions.setItems(sorted);

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldV, newV) ->
                    filteredList.setPredicate(t -> matchesSearch(t, newV)));
        }
    }

    /** Borrower name if the query provides it, otherwise falls back to category. */
    private static String displayBorrower(BorrowedItem t) {
        String n = t.getBorrowerName();
        return (n != null && !n.isBlank()) ? n : t.getCategory();
    }

    private boolean matchesSearch(BorrowedItem t, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        return String.valueOf(t.getBorrowId()).contains(q)
                || contains(t.getEquipmentName(), q)
                || contains(displayBorrower(t), q)
                || contains(t.getStatus(), q);
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    @FXML
    private void loadTransactionData() {
        // Only items the borrower has already clicked "Return item" on show up here.
        transactionList.setAll(equipmentDAO.getPendingReturnTransactions());
        updateMetrics();
    }

    private void updateMetrics() {
        // Metrics cover everything currently out, not just what is waiting to be returned.
        List<BorrowedItem> stillOut = equipmentDAO.getAllActiveTransactions();
        long overdue = stillOut.stream().filter(t -> "Overdue".equalsIgnoreCase(t.getStatus())).count();

        if (lblTotalCheckedOut != null) lblTotalCheckedOut.setText(String.valueOf(stillOut.size()));
        if (lblPendingReturns != null) lblPendingReturns.setText(String.valueOf(transactionList.size()));
        if (lblOverdueItems != null) lblOverdueItems.setText(String.valueOf(overdue));
    }

    private void openReturnModal(BorrowedItem transaction) {
        this.selectedTransaction = transaction;
        lblModalEquipmentName.setText(transaction.getEquipmentName());
        comboCondition.getSelectionModel().selectFirst();

        boolean isOverdue = "Overdue".equalsIgnoreCase(transaction.getStatus());
        lblLateFeeWarning.setVisible(isOverdue);
        lblLateFeeWarning.setManaged(isOverdue);

        modalOverlay.setOpacity(0);
        modalOverlay.setVisible(true);
        FadeTransition ft = new FadeTransition(Duration.millis(180), modalOverlay);
        ft.setToValue(1);
        ft.play();
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
            String itemName = selectedTransaction.getEquipmentName();

            // Anything other than "Good / Undamaged" (minor wear or damaged) goes to the Maintenance Log.
            boolean needsMaintenance = condition != null && !condition.startsWith("Good");

            // Equipment status is updated in the same DB transaction as the return itself.
            boolean success = equipmentDAO.processReturnRequest(
                    selectedTransaction.getBorrowId(), currentAdminId, needsMaintenance);

            if (success) {
                loadTransactionData();
                closeModal();
                if (needsMaintenance) {
                    showSuccessDialog("Sent to Maintenance",
                            "'" + itemName + "' was returned and logged in the Maintenance Log.");
                }
            } else {
                showErrorDialog("Return Error", "Could not process this return. Please refresh the list and try again.");
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

    @FXML
    private void handleViewAccounts(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavAccounts) return;
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isSuperAdmin()) return;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/superadmin/manage_accounts.fxml"));
            Node accountsView = loader.load();

            updateActiveSidebarButton(btnNavAccounts);
            if (dynamicContentArea != null) {
                switchViewWithLock(accountsView);
            }
        } catch (IOException e) {
            System.err.println("[AdminDashboardController] Error loading manage_accounts.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleChangeCredentials(ActionEvent event) {
        CredentialsDialog.show(btnNavHistory.getScene().getWindow(), false);
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
        Button[] buttons = {btnNavReturns, btnNavRequests, btnNavInventory, btnNavMaintenance, btnNavHistory, btnNavAccounts};
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