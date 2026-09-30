package controller.user;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.Equipment;
import model.UserSession;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class UserDashboardController extends BaseController {

    // --- Header & Sidebar FXIDs ---
    @FXML private Label lblUserAvatar;
    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;
    @FXML private Button btnNavCatalog;
    @FXML private Button btnNavMyBorrows;
    @FXML private Button btnNavRequestLog;
    @FXML private Button btnNavSettings;

    // --- Dynamic Content Container FXIDs ---
    @FXML private BorderPane mainContentPane;
    @FXML private StackPane dynamicContentArea;
    @FXML private VBox catalogViewContainer;

    // --- Metric Cards FXIDs ---
    @FXML private Label lblTotalUnits;
    @FXML private Label lblAvailableUnits;
    @FXML private Label lblCheckedOutUnits;

    // --- Search, Filter & Table FXIDs ---
    @FXML private TextField txtSearch;
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterAvailable;
    @FXML private Button btnFilterMaintenance;
    @FXML private Button btnRefreshData;

    @FXML private TableView<Equipment> tblEquipment;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colStatus;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, Equipment> colAction;

    // --- Modal Overlay FXIDs ---
    @FXML private StackPane modalOverlay;
    @FXML private VBox modalDialogBox;
    @FXML private Label lblModalAssetName;
    @FXML private Label lblModalAssetDept;
    @FXML private DatePicker dpReturnTarget;
    @FXML private CheckBox chkCompliance;
    @FXML private Label lblModalError;

    // --- Duration Chips ---
    @FXML private Button btn3Days;
    @FXML private Button btn7Days;
    @FXML private Button btnCustomDate;

    // --- Profile & ID Capture FXIDs ---
    @FXML private ToggleGroup profileTypeGroup;
    @FXML private RadioButton radioStudent;
    @FXML private RadioButton radioFaculty;
    @FXML private TextField txtBorrowerName;
    @FXML private VBox boxStudentFields;
    @FXML private TextField txtStudentId;
    @FXML private ComboBox<String> txtCourse;
    @FXML private VBox boxFacultyFields;
    @FXML private TextField txtEmployeeId;
    @FXML private ComboBox<String> txtDepartment;
    @FXML private Label lblCameraStatus;
    @FXML private StackPane cameraPreviewBox;
    @FXML private ImageView imgCameraPreview;
    @FXML private Button btnCaptureId;
    @FXML private Button btnRetakeSnap;

    // --- Financial & Timestamp FXIDs ---
    @FXML private Label lblBorrowTimestamp;
    @FXML private Label lblCalculatedFee;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<Equipment> masterEquipmentList = FXCollections.observableArrayList();
    private final ObservableList<Equipment> queuedCartList = FXCollections.observableArrayList();
    private List<Equipment> selectedEquipmentListForBorrow = new ArrayList<>();

    private Node myBorrowsView = null;
    private Node requestLogView = null;
    private Node settingsView = null;

    // Navigation Guards
    private boolean isNavigating = false;
    private Button currentActiveButton = null;

    // Background Real-Time Poller
    private ScheduledExecutorService realTimePoller;

    // Camera Fields
    private Webcam webcam;
    private volatile boolean isCameraRunning = false;
    private boolean idCaptured = false;
    private BufferedImage capturedFrame = null;

    private final ObservableList<String> clasPrograms = FXCollections.observableArrayList(
            "AB Political Science",
            "BA Communication",
            "Bachelor of Public Administration",
            "Bachelor of Science in Computer Science",
            "Bachelor of Science in Entertainment and Multimedia Computing",
            "Bachelor of Science in Information System",
            "Bachelor of Science in Information Technology",
            "Bachelor of Science in Mathematics",
            "Bachelor of Science in Psychology"
    );

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            if (lblUserName != null && lblUserName.getScene() != null) {
                lblUserName.getScene().setUserData(this);
            }
        });

        UserSession session = UserSession.getInstance();
        if (session != null) {
            refreshHeaderProfile();
        }

        // Set initial active sidebar button
        currentActiveButton = btnNavCatalog;

        if (dpReturnTarget != null) {
            dpReturnTarget.getEditor().setDisable(true);
            dpReturnTarget.getEditor().setOpacity(1.0);
        }

        if (txtStudentId != null) {
            txtStudentId.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(newVal.toUpperCase())) {
                    txtStudentId.setText(newVal.toUpperCase());
                }
            });
        }

        if (txtCourse != null) txtCourse.setItems(clasPrograms);
        if (txtDepartment != null) txtDepartment.setItems(clasPrograms);

        if (colName != null) colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colCategory != null) colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        if (colSerial != null) colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));

        if (colStatus != null) {
            colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
            colStatus.setCellFactory(column -> new TableCell<Equipment, String>() {
                @Override
                protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);
                    if (empty || status == null) {
                        setText(null); setGraphic(null);
                    } else {
                        Label badge = new Label(status.toUpperCase());
                        badge.getStyleClass().add("status-badge");
                        switch (status.toLowerCase()) {
                            case "available":
                                badge.getStyleClass().add("status-available");
                                break;
                            case "pending approval":
                            case "pending":
                                badge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px;");
                                break;
                            case "in maintenance":
                            case "repair":
                                badge.getStyleClass().add("status-maintenance");
                                break;
                            case "checked out":
                            case "in use":
                                badge.getStyleClass().add("status-checked-out");
                                break;
                            default:
                                badge.getStyleClass().add("status-default");
                                break;
                        }
                        setGraphic(badge); setText(null);
                    }
                }
            });
        }

        // Disable Column Dragging across all columns
        if (tblEquipment != null) {
            tblEquipment.getColumns().forEach(col -> col.setReorderable(false));
        }

        // Real-Time Queue Listener
        queuedCartList.addListener((ListChangeListener<Equipment>) change -> {
            updateCartSidebarBadge();
            if (tblEquipment != null) {
                tblEquipment.refresh();
            }
        });

        // Action Column: Fixed Cell Rendering
        if (colAction != null) {
            colAction.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));
            colAction.setCellFactory(param -> new TableCell<Equipment, Equipment>() {
                private final Button btnSelect = new Button();

                {
                    btnSelect.setPrefWidth(110);
                    btnSelect.setPrefHeight(32);
                }

                @Override
                protected void updateItem(Equipment currentEquipment, boolean empty) {
                    super.updateItem(currentEquipment, empty);

                    if (empty || currentEquipment == null) {
                        setGraphic(null);
                    } else {
                        boolean isAvailable = "Available".equalsIgnoreCase(currentEquipment.getStatus());

                        if (isAvailable) {
                            btnSelect.setDisable(false);
                            boolean isQueued = queuedCartList.stream().anyMatch(e -> e.getId() == currentEquipment.getId());

                            if (isQueued) {
                                btnSelect.setText("✕ Unselect");
                                btnSelect.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
                            } else {
                                btnSelect.setText("+ Select");
                                btnSelect.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
                            }

                            btnSelect.setOnAction(event -> {
                                boolean currentlyQueued = queuedCartList.stream().anyMatch(e -> e.getId() == currentEquipment.getId());
                                if (currentlyQueued) {
                                    queuedCartList.removeIf(e -> e.getId() == currentEquipment.getId());
                                } else {
                                    queuedCartList.add(currentEquipment);
                                }
                            });
                        } else {
                            btnSelect.setText("Unavailable");
                            btnSelect.setDisable(true);
                            btnSelect.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #94a3b8; -fx-font-weight: normal; -fx-background-radius: 20px;");
                            btnSelect.setOnAction(null);
                        }

                        setGraphic(btnSelect);
                    }
                }
            });
        }

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter(newVal));
        }

        loadEquipmentData();
        startRealTimeServerPolling();
        playFadeIn(catalogViewContainer);
    }

    public void refreshHeaderProfile() {
        UserSession session = UserSession.getInstance();
        if (session != null) {
            if (lblUserName != null) {
                lblUserName.setText(session.getFullName() != null ? session.getFullName() : "User");
            }
            if (lblUserRole != null) {
                lblUserRole.setText(session.getRole() != null ? session.getRole().toUpperCase() : "STUDENT");
            }
            if (lblUserAvatar != null && session.getFullName() != null && !session.getFullName().isEmpty()) {
                String[] names = session.getFullName().split(" ");
                String initials = names.length > 1
                        ? ("" + names[0].charAt(0) + names[names.length - 1].charAt(0)).toUpperCase()
                        : ("" + names[0].charAt(0)).toUpperCase();
                lblUserAvatar.setText(initials);
            }
        }
    }

    private void startRealTimeServerPolling() {
        realTimePoller = Executors.newSingleThreadScheduledExecutor();
        realTimePoller.scheduleAtFixedRate(() -> {
            Platform.runLater(this::loadEquipmentDataSilently);
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void loadEquipmentDataSilently() {
        List<Equipment> dbEquipment = equipmentDAO.getAllEquipment();
        masterEquipmentList.clear();
        masterEquipmentList.addAll(dbEquipment);

        if (txtSearch != null && !txtSearch.getText().isBlank()) {
            applySearchFilter(txtSearch.getText());
        } else {
            if (tblEquipment != null) {
                tblEquipment.setItems(null); // Force invalidate internal JavaFX cell cache
                tblEquipment.setItems(masterEquipmentList);
                tblEquipment.refresh();
            }
        }
        updateMetrics();
    }

    @FXML
    private void handleManualRefresh(ActionEvent event) {
        if (btnRefreshData != null) {
            btnRefreshData.setDisable(true);
            btnRefreshData.setText("🔄 Refreshing...");
        }

        loadEquipmentData();

        PauseTransition pause = new PauseTransition(Duration.millis(300));
        pause.setOnFinished(e -> {
            if (btnRefreshData != null) {
                btnRefreshData.setDisable(false);
                btnRefreshData.setText("🔄 Refresh Data");
            }
        });
        pause.play();
    }

    public void loadEquipmentData() {
        loadEquipmentDataSilently();
    }

    private void updateMetrics() {
        int total = masterEquipmentList.size();
        long available = masterEquipmentList.stream().filter(e -> "Available".equalsIgnoreCase(e.getStatus())).count();
        long checkedOut = masterEquipmentList.stream().filter(e -> "Checked Out".equalsIgnoreCase(e.getStatus()) || "Borrowed".equalsIgnoreCase(e.getStatus())).count();

        if (lblTotalUnits != null) lblTotalUnits.setText(String.valueOf(total));
        if (lblAvailableUnits != null) lblAvailableUnits.setText(String.valueOf(available));
        if (lblCheckedOutUnits != null) lblCheckedOutUnits.setText(String.valueOf(checkedOut));
    }

    private void applySearchFilter(String query) {
        if (query == null || query.isBlank()) {
            tblEquipment.setItems(masterEquipmentList);
            return;
        }
        String lowerQuery = query.toLowerCase().trim();
        ObservableList<Equipment> filteredList = FXCollections.observableArrayList();
        for (Equipment eq : masterEquipmentList) {
            if (eq.getName().toLowerCase().contains(lowerQuery) ||
                    eq.getCategory().toLowerCase().contains(lowerQuery) ||
                    eq.getSerialNumber().toLowerCase().contains(lowerQuery) ||
                    eq.getStatus().toLowerCase().contains(lowerQuery)) {
                filteredList.add(eq);
            }
        }
        tblEquipment.setItems(filteredList);
    }

    private void updateCartSidebarBadge() {
        if (btnNavRequestLog != null) {
            int cartSize = queuedCartList.size();
            if (cartSize > 0) {
                btnNavRequestLog.setText("Borrow Request (" + cartSize + ")");
            } else {
                btnNavRequestLog.setText("Borrow Request");
            }
        }
    }

    @FXML
    private void filterAll(ActionEvent event) {
        if (txtSearch != null) txtSearch.clear();
        tblEquipment.setItems(masterEquipmentList);
        updateActiveFilterChip(btnFilterAll);
    }

    @FXML
    private void filterAvailable(ActionEvent event) {
        filterByStatus("Available");
        updateActiveFilterChip(btnFilterAvailable);
    }

    @FXML
    private void filterRepair(ActionEvent event) {
        filterByStatus("In Maintenance");
        updateActiveFilterChip(btnFilterMaintenance);
    }

    private void filterByStatus(String status) {
        ObservableList<Equipment> filtered = FXCollections.observableArrayList();
        for (Equipment eq : masterEquipmentList) {
            if (status.equalsIgnoreCase(eq.getStatus())) filtered.add(eq);
        }
        tblEquipment.setItems(filtered);
    }

    private void updateActiveFilterChip(Button activeButton) {
        Button[] chips = {btnFilterAll, btnFilterAvailable, btnFilterMaintenance};
        for (Button chip : chips) {
            if (chip != null) {
                chip.getStyleClass().remove("filter-chip-active");
                if (!chip.getStyleClass().contains("filter-chip")) {
                    chip.getStyleClass().add("filter-chip");
                }
            }
        }
        if (activeButton != null) {
            activeButton.getStyleClass().remove("filter-chip");
            if (!activeButton.getStyleClass().contains("filter-chip-active")) {
                activeButton.getStyleClass().add("filter-chip-active");
            }
        }
    }

    // --- NAVIGATION HANDLERS WITH ACTIVE VIEW CHECK & SPAM LOCK ---

    @FXML
    private void handleViewCatalog(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavCatalog) return;

        updateActiveSidebarButton(btnNavCatalog);
        if (dynamicContentArea != null && catalogViewContainer != null) {
            switchViewWithLock(catalogViewContainer);
        }
        loadEquipmentData();
    }

    @FXML
    private void handleViewBorrowRequest(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavRequestLog) return;

        updateActiveSidebarButton(btnNavRequestLog);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/user/borrow_request_cart.fxml"));
            Node cartView = loader.load();

            BorrowRequestCartController cartController = loader.getController();
            cartController.setCartData(queuedCartList);

            if (dynamicContentArea != null) {
                switchViewWithLock(cartView);
            }
        } catch (IOException e) {
            System.err.println("[UserDashboardController] Error loading borrow_request_cart.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewMyBorrows(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavMyBorrows) return;

        updateActiveSidebarButton(btnNavMyBorrows);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/user/my_borrows.fxml"));
            Node freshMyBorrowsView = loader.load();
            if (dynamicContentArea != null) {
                switchViewWithLock(freshMyBorrowsView);
            }
        } catch (IOException e) {
            System.err.println("[UserDashboardController] Error loading my_borrows.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewRequestLog(ActionEvent event) {
        handleViewBorrowRequest(event);
    }

    @FXML
    private void handleViewSettings(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavSettings) return;

        try {
            if (settingsView == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/user/user_settings.fxml"));
                settingsView = loader.load();
            }

            updateActiveSidebarButton(btnNavSettings);
            if (dynamicContentArea != null) {
                switchViewWithLock(settingsView);
            }
        } catch (IOException e) {
            System.err.println("[UserDashboardController] Error loading user_settings.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Safely switches dynamic views while locking navigation buttons until the transition completes.
     */
    private void switchViewWithLock(Node targetView) {
        if (dynamicContentArea == null || targetView == null) return;

        isNavigating = true; // Engage Navigation Lock

        dynamicContentArea.getChildren().setAll(targetView);

        FadeTransition fade = new FadeTransition(Duration.millis(200), targetView);
        fade.setFromValue(0.3);
        fade.setToValue(1.0);
        fade.setOnFinished(e -> isNavigating = false); // Release Lock
        fade.play();
    }

    private void updateActiveSidebarButton(Button activeButton) {
        currentActiveButton = activeButton;
        Button[] buttons = {btnNavCatalog, btnNavMyBorrows, btnNavRequestLog, btnNavSettings};
        for (Button btn : buttons) {
            if (btn != null) {
                btn.getStyleClass().remove("sidebar-btn-active");
                if (!btn.getStyleClass().contains("sidebar-btn")) btn.getStyleClass().add("sidebar-btn");
            }
        }
        if (activeButton != null) {
            activeButton.getStyleClass().remove("sidebar-btn");
            if (!activeButton.getStyleClass().contains("sidebar-btn-active")) activeButton.getStyleClass().add("sidebar-btn-active");
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) throws IOException {
        stopRealTimePoller();
        closeWebcamHardware();
        UserSession.cleanUserSession();
        navigateTo("/com/borrowclas/clasborrowingsystem/fxml/auth/login.fxml");
    }

    private void stopRealTimePoller() {
        if (realTimePoller != null && !realTimePoller.isShutdown()) {
            realTimePoller.shutdownNow();
        }
    }

    @FXML
    private void closeModal(ActionEvent event) {
        closeWebcamHardware();
        if (modalOverlay != null) modalOverlay.setVisible(false);
        selectedEquipmentListForBorrow.clear();
    }

    @FXML
    private void handleProfileTypeChange() {
        boolean isStudent = radioStudent != null && radioStudent.isSelected();
        if (boxStudentFields != null) {
            boxStudentFields.setVisible(isStudent);
            boxStudentFields.setManaged(isStudent);
        }
        if (boxFacultyFields != null) {
            boxFacultyFields.setVisible(!isStudent);
            boxFacultyFields.setManaged(!isStudent);
        }
    }

    @FXML
    private void handleCaptureIdSnap() {
        if (!isCameraRunning) {
            webcam = Webcam.getDefault();
            if (webcam != null) {
                webcam.setViewSize(WebcamResolution.VGA.getSize());
                webcam.open();
                isCameraRunning = true;

                new Thread(() -> {
                    while (isCameraRunning) {
                        BufferedImage image = webcam.getImage();
                        if (image != null) {
                            capturedFrame = image;
                            Image fxImage = SwingFXUtils.toFXImage(image, null);
                            Platform.runLater(() -> {
                                if (imgCameraPreview != null) imgCameraPreview.setImage(fxImage);
                            });
                        }
                        try { Thread.sleep(40); } catch (InterruptedException ignored) {}
                    }
                }).start();

                if (lblCameraStatus != null) lblCameraStatus.setVisible(false);
                if (btnCaptureId != null) btnCaptureId.setText("📸 Snap Photo");
            } else {
                if (lblModalError != null) lblModalError.setText("No laptop webcam hardware detected.");
            }
        } else {
            isCameraRunning = false;
            idCaptured = true;
            closeWebcamHardware();

            if (lblCameraStatus != null) {
                lblCameraStatus.setText("✓ Student ID Photo Attached");
                lblCameraStatus.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 11px;");
                lblCameraStatus.setVisible(true);
            }

            if (btnCaptureId != null) {
                btnCaptureId.setVisible(false);
                btnCaptureId.setManaged(false);
            }

            if (btnRetakeSnap != null) {
                btnRetakeSnap.setVisible(true);
                btnRetakeSnap.setManaged(true);
            }
        }
    }

    @FXML
    private void handleRetakeSnap() {
        idCaptured = false;
        isCameraRunning = false;
        capturedFrame = null;
        closeWebcamHardware();

        if (imgCameraPreview != null) {
            imgCameraPreview.setImage(null);
        }

        if (lblCameraStatus != null) {
            lblCameraStatus.setText("📷 Click to Capture ID Snapshot");
            lblCameraStatus.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11px;");
            lblCameraStatus.setVisible(true);
        }

        if (btnCaptureId != null) {
            btnCaptureId.setText("📷 Capture ID / Take Snap");
            btnCaptureId.setVisible(true);
            btnCaptureId.setManaged(true);
        }

        if (btnRetakeSnap != null) {
            btnRetakeSnap.setVisible(false);
            btnRetakeSnap.setManaged(false);
        }
    }

    @FXML
    private void calculateTransactionFees() {
        if (dpReturnTarget != null && dpReturnTarget.getValue() != null) {
            LocalDate today = LocalDate.now();
            LocalDate returnDate = dpReturnTarget.getValue();
            long days = ChronoUnit.DAYS.between(today, returnDate);

            if (days < 1) days = 1;

            if (days == 3) {
                updateActiveDurationChip(btn3Days);
            } else if (days == 7) {
                updateActiveDurationChip(btn7Days);
            } else {
                updateActiveDurationChip(btnCustomDate);
            }

            int itemCount = selectedEquipmentListForBorrow.isEmpty() ? 1 : selectedEquipmentListForBorrow.size();
            double baseFeePerItem = days * 20.00;
            double totalBatchFee = baseFeePerItem * itemCount;

            if (lblCalculatedFee != null) {
                if (itemCount > 1) {
                    lblCalculatedFee.setText(String.format("₱%.2f (%d days x %d items)", totalBatchFee, days, itemCount));
                } else {
                    lblCalculatedFee.setText(String.format("₱%.2f (%d days)", totalBatchFee, days));
                }
            }
            if (lblBorrowTimestamp != null) {
                lblBorrowTimestamp.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
        }
    }

    private void updateActiveDurationChip(Button activeButton) {
        Button[] chips = {btn3Days, btn7Days, btnCustomDate};
        for (Button chip : chips) {
            if (chip != null) {
                chip.getStyleClass().remove("filter-chip-active");
                if (!chip.getStyleClass().contains("filter-chip")) {
                    chip.getStyleClass().add("filter-chip");
                }
            }
        }
        if (activeButton != null) {
            activeButton.getStyleClass().remove("filter-chip");
            if (!activeButton.getStyleClass().contains("filter-chip-active")) {
                activeButton.getStyleClass().add("filter-chip-active");
            }
        }
    }

    @FXML
    private void select3Days(ActionEvent event) {
        updateActiveDurationChip(btn3Days);
        if (dpReturnTarget != null) {
            dpReturnTarget.setDisable(true);
            dpReturnTarget.setValue(LocalDate.now().plusDays(3));
            calculateTransactionFees();
        }
    }

    @FXML
    private void select7Days(ActionEvent event) {
        updateActiveDurationChip(btn7Days);
        if (dpReturnTarget != null) {
            dpReturnTarget.setDisable(true);
            dpReturnTarget.setValue(LocalDate.now().plusDays(7));
            calculateTransactionFees();
        }
    }

    @FXML
    private void selectCustomDate(ActionEvent event) {
        updateActiveDurationChip(btnCustomDate);
        if (dpReturnTarget != null) {
            dpReturnTarget.setDisable(false);
            dpReturnTarget.requestFocus();
            dpReturnTarget.show();
        }
    }

    @FXML
    private void confirmBorrowCommit(ActionEvent event) {
        if (selectedEquipmentListForBorrow == null || selectedEquipmentListForBorrow.isEmpty()) {
            if (lblModalError != null) lblModalError.setText("Error: No equipment selected.");
            triggerShakeAnimation(modalDialogBox);
            return;
        }

        String borrowerName = txtBorrowerName != null ? txtBorrowerName.getText().trim() : "";
        if (borrowerName.isEmpty()) {
            if (lblModalError != null) lblModalError.setText("Please enter the full borrower name.");
            triggerShakeAnimation(modalDialogBox);
            return;
        }

        boolean isStudent = radioStudent != null && radioStudent.isSelected();
        String borrowerType = isStudent ? "STUDENT" : "FACULTY";
        String borrowerIdNumber = "";
        String programOrDept = "";

        if (isStudent) {
            borrowerIdNumber = txtStudentId != null ? txtStudentId.getText().trim() : "";
            programOrDept = txtCourse != null && txtCourse.getValue() != null ? txtCourse.getValue().trim() : "";

            if (borrowerIdNumber.isEmpty() || programOrDept.isEmpty()) {
                if (lblModalError != null) lblModalError.setText("Please complete Student ID and select an Academic Program.");
                triggerShakeAnimation(modalDialogBox);
                return;
            }

            if (!borrowerIdNumber.matches("^\\d{8}-[A-Za-z0-9]$")) {
                if (lblModalError != null) lblModalError.setText("Student Number format must be like: 20250045-N");
                triggerShakeAnimation(modalDialogBox);
                return;
            }
        } else {
            borrowerIdNumber = txtEmployeeId != null ? txtEmployeeId.getText().trim() : "";
            programOrDept = txtDepartment != null && txtDepartment.getValue() != null ? txtDepartment.getValue().trim() : "";

            if (borrowerIdNumber.isEmpty() || programOrDept.isEmpty()) {
                if (lblModalError != null) lblModalError.setText("Please complete Employee ID and select a Department.");
                triggerShakeAnimation(modalDialogBox);
                return;
            }
        }

        if (!idCaptured) {
            if (lblModalError != null) lblModalError.setText("ID Verification required: Please take an ID snapshot.");
            triggerShakeAnimation(modalDialogBox);
            return;
        }

        if (chkCompliance != null && !chkCompliance.isSelected()) {
            if (lblModalError != null) lblModalError.setText("You must accept the terms of responsibility.");
            triggerShakeAnimation(modalDialogBox);
            return;
        }

        LocalDate returnTargetDate = dpReturnTarget != null ? dpReturnTarget.getValue() : null;
        if (returnTargetDate == null || returnTargetDate.isBefore(LocalDate.now())) {
            if (lblModalError != null) lblModalError.setText("Please select a valid future return date.");
            triggerShakeAnimation(modalDialogBox);
            return;
        }

        long days = ChronoUnit.DAYS.between(LocalDate.now(), returnTargetDate);
        if (days < 1) days = 1;
        double feePerItem = days * 20.00;

        byte[] idSnapshotBytes = getSnapshotBytes();

        UserSession session = UserSession.getInstance();
        int activeUserId = (session != null && session.getUserId() > 0) ? session.getUserId() : 1;

        List<Integer> equipmentIds = selectedEquipmentListForBorrow.stream()
                .map(Equipment::getId)
                .collect(Collectors.toList());

        boolean success = equipmentDAO.createBatchBorrowRequest(
                equipmentIds,
                borrowerName,
                borrowerType,
                borrowerIdNumber,
                programOrDept,
                returnTargetDate,
                activeUserId,
                feePerItem,
                idSnapshotBytes
        );

        if (success) {
            String successMessage = (equipmentIds.size() == 1)
                    ? "Borrow request for '" + selectedEquipmentListForBorrow.get(0).getName() + "' logged successfully."
                    : "Borrow requests for " + equipmentIds.size() + " items logged successfully.";

            loadEquipmentData();
            closeModal(event);
            showSuccessDialog("Request Submitted", successMessage);
        } else {
            if (lblModalError != null) lblModalError.setText("Database error: Could not process request.");
            triggerShakeAnimation(modalDialogBox);
        }
    }

    private byte[] getSnapshotBytes() {
        if (capturedFrame == null) return null;

        try {
            int targetWidth = 320;
            int targetHeight = 240;
            BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

            Graphics2D g2d = resizedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(capturedFrame, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(resizedImage, "jpg", baos);
                return baos.toByteArray();
            }
        } catch (IOException e) {
            System.err.println("[Snapshot Error] Failed to compress image to bytes: " + e.getMessage());
            return null;
        }
    }

    private void closeWebcamHardware() {
        isCameraRunning = false;
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }
}