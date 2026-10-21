package controller.admin;

import controller.BaseController;
import db.DbHealth;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
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
import util.ActionFilter;
import util.CredentialsDialog;
import util.LiveRefresh;
import util.RequestBadge;
import util.TableCells;

import controller.dialog.ConfirmDialogController;

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
    @FXML private Button btnNavCatalog;
    @FXML private Button btnNavInventory;
    @FXML private Button btnNavMaintenance;
    @FXML private Button btnNavHistory;
    @FXML private Button btnNavAccounts;
    @FXML private Button btnNavPasswordRequests; // <-- Add this field
    @FXML private Button btnNavFees;
    @FXML private Button btnNavUsage;
    @FXML private StackPane dynamicContentArea;
    @FXML private VBox returnsViewContainer;

    // --- Metrics ---
    @FXML private Label lblTotalCheckedOut;
    @FXML private Label lblPendingReturns;
    @FXML private Label lblOverdueItems;

    // --- Search + Table ---
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbActionFilter;
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
    @FXML private VBox boxDamageReport;
    @FXML private Label lblDamageLabel;
    @FXML private TextArea txtDamageReport;
    @FXML private Label lblModalError;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<BorrowedItem> transactionList = FXCollections.observableArrayList();
    private FilteredList<BorrowedItem> filteredList;
    private ActionFilter<BorrowedItem> actionFilter;
    private Timeline badgeTimer;
    private LiveRefresh.Handle returnsWatcher;
    private boolean returnsRefreshRunning = false;
    private BorrowedItem selectedTransaction;

    private boolean isNavigating = false;
    private Button currentActiveButton = null;

    @FXML
    public void initialize() {
        UserSession session = UserSession.getInstance();
        if (session != null) {
            lblUserName.setText(session.getFullName() != null ? session.getFullName() : "Admin");
            boolean superAdmin = session.isSuperAdmin();
            lblUserRole.setText(session.getRoleLabel());
            if (btnNavAccounts != null) {
                btnNavAccounts.setVisible(superAdmin);
                btnNavAccounts.setManaged(superAdmin);
            }
            if (btnNavFees != null) {
                btnNavFees.setVisible(superAdmin);
                btnNavFees.setManaged(superAdmin);
            }
            if (btnNavPasswordRequests != null) { // <-- Add this block
                btnNavPasswordRequests.setVisible(superAdmin);
                btnNavPasswordRequests.setManaged(superAdmin);
            }
        }

        currentActiveButton = btnNavReturns;
        comboCondition.setItems(FXCollections.observableArrayList("Good / Undamaged", "Minor Wear", "Damaged (Needs Repair)"));

        comboCondition.valueProperty().addListener((obs, o, n) -> updateDamageReportVisibility());

        setupTableColumns();
        setupSearch();
        loadTransactionData();
        setupRequestBadge();

        returnsWatcher = LiveRefresh.watch(tblTransactions, LiveRefresh.DEFAULT_SECONDS, false, this::refreshReturnsInBackground);
    }

    private void setupRequestBadge() {
        if (btnNavRequests == null) return;

        Label badge = new Label();
        badge.getStyleClass().add("nav-badge");
        badge.textProperty().bind(Bindings.createStringBinding(
                () -> RequestBadge.PENDING.get() > 99 ? "99+" : String.valueOf(RequestBadge.PENDING.get()),
                RequestBadge.PENDING));
        badge.visibleProperty().bind(RequestBadge.PENDING.greaterThan(0));
        badge.managedProperty().bind(badge.visibleProperty());

        btnNavRequests.setGraphic(badge);
        btnNavRequests.setContentDisplay(ContentDisplay.RIGHT);
        btnNavRequests.setGraphicTextGap(10);

        refreshPendingBadge();
        badgeTimer = new Timeline(new KeyFrame(Duration.seconds(5), e -> refreshPendingBadge()));
        badgeTimer.setCycleCount(Animation.INDEFINITE);
        badgeTimer.play();
    }

    private void refreshPendingBadge() {
        Task<Integer> task = new Task<>() {
            @Override protected Integer call() {
                DbHealth.reset();
                int count = equipmentDAO.countPendingRequests();
                return DbHealth.failed() ? null : count;
            }
        };
        task.setOnSucceeded(e -> { if (task.getValue() != null) RequestBadge.PENDING.set(task.getValue()); });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void stopBadgeTimer() {
        if (badgeTimer != null) badgeTimer.stop();
        RequestBadge.PENDING.set(0);
    }

    private void setupTableColumns() {
        colTransId.setCellValueFactory(new PropertyValueFactory<>("borrowId"));
        colEquipment.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colBorrower.setCellValueFactory(d -> new SimpleStringProperty(displayBorrower(d.getValue())));
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colTransId.setCellFactory(TableCells.<BorrowedItem, Integer>idCell());
        colBorrower.setCellFactory(TableCells.avatarNameCell());
        colEquipment.setCellFactory(TableCells.primaryCell());
        colDueDate.setCellFactory(TableCells.<BorrowedItem, LocalDate>dueDateCell(
                t -> "Overdue".equalsIgnoreCase(t.getStatus())));
        colStatus.setCellFactory(TableCells.<BorrowedItem>statusPill());
        colAction.setCellFactory(TableCells.<BorrowedItem>actionButton("Process Return", this::openReturnModal));

        TableCells.modernize(tblTransactions);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    private void setupSearch() {
        filteredList = new FilteredList<>(transactionList, t -> true);
        SortedList<BorrowedItem> sorted = new SortedList<>(filteredList);
        sorted.comparatorProperty().bind(tblTransactions.comparatorProperty());
        tblTransactions.setItems(sorted);

        actionFilter = new ActionFilter<>(cmbActionFilter, transactionList, filteredList, BorrowedItem::getStatus);
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldV, newV) ->
                    actionFilter.setSearch(t -> matchesSearch(t, newV)));
        }
    }

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
        transactionList.setAll(equipmentDAO.getAllActiveTransactions());
        updateMetrics();
    }

    private void updateMetrics() {
        showMetrics(equipmentDAO.getAllActiveTransactions());
    }

    private record ReturnsSnapshot(List<BorrowedItem> pending, List<BorrowedItem> stillOut) {}

    private void refreshReturnsInBackground() {
        if (returnsRefreshRunning) return;
        returnsRefreshRunning = true;
        LiveRefresh.background(
                () -> {
                    List<BorrowedItem> active = equipmentDAO.getAllActiveTransactions();
                    return new ReturnsSnapshot(active, active);
                },
                snap -> {
                    returnsRefreshRunning = false;
                    if (snap == null) return;
                    if (!LiveRefresh.fingerprint(snap.pending()).equals(LiveRefresh.fingerprint(transactionList))) {
                        transactionList.setAll(snap.pending());
                    }
                    showMetrics(snap.stillOut());
                });
    }

    private void showMetrics(List<BorrowedItem> stillOut) {
        long overdue = stillOut.stream().filter(t -> "Overdue".equalsIgnoreCase(t.getStatus())).count();

        if (lblTotalCheckedOut != null) lblTotalCheckedOut.setText(String.valueOf(stillOut.size()));
        if (lblPendingReturns != null) lblPendingReturns.setText(String.valueOf(transactionList.size()));
        if (lblOverdueItems != null) lblOverdueItems.setText(String.valueOf(overdue));
    }

    private void openReturnModal(BorrowedItem transaction) {
        this.selectedTransaction = transaction;
        lblModalEquipmentName.setText(transaction.getEquipmentName());
        comboCondition.getSelectionModel().selectFirst();

        boolean isOverdue = transaction.getLateDays() > 0;
        if (isOverdue) {
            lblLateFeeWarning.setText(String.format("Returned %d day%s late. Late penalty: \u20B1%,.2f (\u20B1%,.2f per day).",
                    transaction.getLateDays(), transaction.getLateDays() == 1 ? "" : "s",
                    transaction.getLatePenalty(), transaction.getLatePenaltyPerDay()));
        }
        lblLateFeeWarning.setVisible(isOverdue);
        lblLateFeeWarning.setManaged(isOverdue);

        txtDamageReport.clear();
        lblModalError.setText("");
        updateDamageReportVisibility();

        modalOverlay.setOpacity(0);
        modalOverlay.setVisible(true);
        FadeTransition ft = new FadeTransition(Duration.millis(180), modalOverlay);
        ft.setToValue(1);
        ft.play();
    }

    private boolean isDamagedSelected() {
        String c = comboCondition.getValue();
        return c != null && c.startsWith("Damaged");
    }

    private void updateDamageReportVisibility() {
        String c = comboCondition.getValue();
        boolean show = c != null && !c.startsWith("Good");
        boxDamageReport.setVisible(show);
        boxDamageReport.setManaged(show);
        lblDamageLabel.setText(isDamagedSelected()
                ? "Damage report (required): describe the specific damage"
                : "Notes on the wear (optional)");
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

            boolean needsMaintenance = condition != null && !condition.startsWith("Good");

            String report = txtDamageReport.getText() == null ? "" : txtDamageReport.getText().trim();
            if (isDamagedSelected() && report.length() < 15) {
                util.InputRules.setInvalid(txtDamageReport, true);
                lblModalError.setText("Please write a damage report (at least a short sentence) describing the specific damage.");
                return;
            }
            util.InputRules.setInvalid(txtDamageReport, false);

            String confirmMsg = String.format("Are you sure you want to finalize the return for '%s'?\n\nCondition marked as: %s", itemName, condition);
            boolean userSaidYes = ConfirmDialogController.show("Confirm Return", confirmMsg, modalOverlay.getScene().getWindow());

            if (!userSaidYes) {
                return;
            }

            boolean success = equipmentDAO.processReturnRequest(
                    selectedTransaction.getBorrowId(), currentAdminId, needsMaintenance, condition, report);

            if (success) {
                loadTransactionData();
                closeModal();
                if (needsMaintenance) {
                    showSuccessDialog("Sent to Maintenance",
                            "'" + itemName + "' was returned and logged in the Maintenance Log.");
                } else {
                    showSuccessDialog("Return Successful", "'" + itemName + "' has been successfully checked in.");
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
        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/admin/manage_requests.fxml", btnNavRequests);
    }

    @FXML
    private void handleViewCatalog(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavCatalog) return;

        // the single Borrow Equipment screen: pick equipment, fill in the borrower, reserve
        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/user/borrow_equipment.fxml", btnNavCatalog);
    }

    @FXML
    private void handleViewInventory(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavInventory) return;
        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/admin/admin_inventory.fxml", btnNavInventory);
    }

    @FXML
    private void handleViewMaintenance(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavMaintenance) return;
        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/admin/maintenance_log.fxml", btnNavMaintenance);
    }

    @FXML
    private void handleViewHistory(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavHistory) return;
        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/admin/admin_history.fxml", btnNavHistory);
    }

    @FXML
    private void handleViewUsage(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavUsage) return;
        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/admin/item_usage.fxml", btnNavUsage);
    }

    @FXML
    private void handleViewAccounts(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavAccounts) return;
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isSuperAdmin()) return;
        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/superadmin/manage_accounts.fxml", btnNavAccounts);
    }

    @FXML
    private void handleViewPasswordRequests(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavPasswordRequests) return;
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isSuperAdmin()) return;

        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/superadmin/password_requests.fxml", btnNavPasswordRequests);
    }

    @FXML
    private void handleViewFees(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavFees) return;
        UserSession session = UserSession.getInstance();
        if (session == null || !session.isSuperAdmin()) return;
        loadViewIntoCenter("/com/borrowclas/clasborrowingsystem/fxml/superadmin/fee_settings.fxml", btnNavFees);
    }

    @FXML
    private void handleChangeCredentials(ActionEvent event) {
        CredentialsDialog.show(btnNavHistory.getScene().getWindow(), false);
    }

    private void loadViewIntoCenter(String fxmlPath, Button navButton) {
        try {
            // Try loading via resource URL
            java.net.URL fxmlUrl = getClass().getResource(fxmlPath);

            // If null, try absolute path fallback based on standard package structure
            if (fxmlUrl == null && !fxmlPath.startsWith("/")) {
                fxmlUrl = getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/" + fxmlPath.replace("fxml/", ""));
            }

            if (fxmlUrl == null) {
                System.err.println("[AdminDashboardController Error] FXML file could not be found: " + fxmlPath);
                showErrorDialog("Navigation Error", "Could not find the view file: " + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Node view = loader.load();
            updateActiveSidebarButton(navButton);
            if (dynamicContentArea != null) {
                switchViewWithLock(view);
            }
        } catch (IOException e) {
            System.err.println("[AdminDashboardController] Error loading " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
            showErrorDialog("Navigation Error", "Failed to load view: " + e.getMessage());
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
        Button[] buttons = {
                btnNavReturns, btnNavRequests, btnNavCatalog,
                btnNavInventory, btnNavMaintenance, btnNavHistory, btnNavUsage,
                btnNavAccounts, btnNavFees, btnNavPasswordRequests // <-- Add btnNavPasswordRequests here
        };
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
        controller.user.BorrowEquipmentController.clearQueue();
        stopBadgeTimer();
        if (returnsWatcher != null) returnsWatcher.stop();
        UserSession.cleanUserSession();
        navigateTo("/com/borrowclas/clasborrowingsystem/fxml/auth/login.fxml");
    }
}