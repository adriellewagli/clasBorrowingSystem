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
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
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
import util.ReserveDialog;
import util.TableCells;

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
import java.util.Map;
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
    @FXML private Button btnNavMyRequests;
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
    private javafx.collections.transformation.FilteredList<Equipment> catalogFiltered;
    private javafx.collections.transformation.SortedList<Equipment> catalogSorted;
    private String statusChip = null;      // null = all
    private String searchText = "";
    private boolean catalogLoading = false;
    private boolean catalogReloadQueued = false;
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
            colStatus.setCellFactory(TableCells.<Equipment, String>withLabelMeasure(column -> new TableCell<Equipment, String>() {
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
            }, 24, v -> String.valueOf(v).toUpperCase(), "status-badge"));
        }

        if (tblEquipment != null) {
            tblEquipment.getColumns().forEach(col -> col.setReorderable(false));
            // the Select / Reserve button column must never be squeezed
            if (colAction != null) colAction.setMinWidth(150);
            // columns size to their text; when the window is narrow the table scrolls sideways
            TableCells.fitColumns(tblEquipment);
        }

        queuedCartList.addListener((ListChangeListener<Equipment>) change -> {
            updateCartSidebarBadge();
            if (tblEquipment != null) {
                tblEquipment.refresh();
            }
        });

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
                        } else if ("Checked Out".equalsIgnoreCase(currentEquipment.getStatus())
                                || "Pending Approval".equalsIgnoreCase(currentEquipment.getStatus())) {
                            // Out right now (or already requested): it can be reserved for a later date.
                            btnSelect.setDisable(false);
                            btnSelect.setText("Reserve");
                            btnSelect.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #2563eb; -fx-border-color: #2563eb; -fx-border-radius: 20px; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
                            btnSelect.setOnAction(event -> openReserveDialog(currentEquipment));
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

        // one filtered + sortable view over the catalog, so the status chips, the search box and the
        // 5-second refresh all work together (a refresh used to reset the chosen filter)
        catalogFiltered = new FilteredList<>(masterEquipmentList, e -> true);
        catalogSorted = new SortedList<>(catalogFiltered);
        if (tblEquipment != null) {
            catalogSorted.comparatorProperty().bind(tblEquipment.comparatorProperty());
            tblEquipment.setItems(catalogSorted);
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
                lblUserRole.setText(session.getRoleLabel());
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

    /**
     * Reads the catalog in the background and updates the table only if something changed, so the
     * window doesn't freeze, the scroll position stays put and the chosen filter is kept.
     */
    private void loadEquipmentDataSilently() {
        if (catalogLoading) { catalogReloadQueued = true; return; }
        catalogLoading = true;
        util.LiveRefresh.background(equipmentDAO::getAllEquipment, fresh -> {
            catalogLoading = false;
            if (fresh != null) {
                if (!util.LiveRefresh.fingerprint(fresh).equals(util.LiveRefresh.fingerprint(masterEquipmentList))) {
                    masterEquipmentList.setAll(fresh);
                }
                if (tblEquipment != null) tblEquipment.refresh(); // Select / Unselect buttons follow the cart
                updateMetrics();
            }
            if (catalogReloadQueued) {
                catalogReloadQueued = false;
                loadEquipmentDataSilently();
            }
        });
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
        searchText = query == null ? "" : query.toLowerCase().trim();
        refilterCatalog();
    }

    /** Status chip + search box, applied together. */
    private void refilterCatalog() {
        if (catalogFiltered == null) return;
        final String chip = statusChip;
        final String q = searchText;
        catalogFiltered.setPredicate(eq ->
                (chip == null || chip.equalsIgnoreCase(eq.getStatus()))
                        && (q.isEmpty()
                        || has(eq.getName(), q) || has(eq.getCategory(), q)
                        || has(eq.getSerialNumber(), q) || has(eq.getStatus(), q)));
    }

    private static boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
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
        statusChip = null;
        refilterCatalog();
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
        statusChip = status;
        refilterCatalog();
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

    private void openReserveDialog(Equipment item) {
        if (item == null) return;
        javafx.stage.Window owner = tblEquipment != null && tblEquipment.getScene() != null
                ? tblEquipment.getScene().getWindow() : null;
        if (ReserveDialog.show(owner, item)) {
            loadEquipmentData();
            showSuccessDialog("Reservation Submitted",
                    "Your reservation for '" + item.getName() + "' is waiting for admin approval. "
                            + "You can see it (and cancel it) under My Requests.");
        }
    }

    @FXML
    private void handleViewMyRequests(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavMyRequests) return;

        updateActiveSidebarButton(btnNavMyRequests);
        try {
            // loaded fresh every time so it always shows what is still waiting
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/user/current_requests.fxml"));
            Node requestsView = loader.load();
            if (dynamicContentArea != null) {
                switchViewWithLock(requestsView);
            }
        } catch (Exception e) {
            System.err.println("[UserDashboardController] Error loading current_requests.fxml: " + e.getMessage());
            e.printStackTrace();
            // don't leave the sidebar stuck on a tab that never opened
            currentActiveButton = null;
            isNavigating = false;
            showErrorDialog("Could not open My Requests", String.valueOf(e.getMessage()));
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
        Button[] buttons = {btnNavCatalog, btnNavMyBorrows, btnNavRequestLog, btnNavMyRequests, btnNavSettings};
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

        Map<Integer, LocalDate> dueDates = new java.util.HashMap<>();
        for (int id : equipmentIds) dueDates.put(id, returnTargetDate);
        String submitError = equipmentDAO.submitBatchBorrowRequest(
                equipmentIds, dueDates, borrowerName, borrowerType, borrowerIdNumber,
                programOrDept, activeUserId, idSnapshotBytes);
        boolean success = submitError == null;

        if (success) {
            String successMessage = (equipmentIds.size() == 1)
                    ? "Borrow request for '" + selectedEquipmentListForBorrow.get(0).getName() + "' logged successfully."
                    : "Borrow requests for " + equipmentIds.size() + " items logged successfully.";

            loadEquipmentData();
            closeModal(event);
            showSuccessDialog("Request Submitted", successMessage);
        } else {
            if (lblModalError != null) lblModalError.setText(submitError);
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