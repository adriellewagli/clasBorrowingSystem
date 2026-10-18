package controller.user;

import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;
import model.Equipment;
import util.ReserveDialog;
import util.TableCells;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EquipmentCatalogController {

    // --- Metric Cards ---
    @FXML private Label lblTotalUnits;
    @FXML private Label lblAvailableUnits;
    @FXML private Label lblCheckedOutUnits;

    // --- Search, Filter & Table ---
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

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<Equipment> masterEquipmentList = FXCollections.observableArrayList();
    private FilteredList<Equipment> catalogFiltered;
    private SortedList<Equipment> catalogSorted;
    private String statusChip = null;
    private String searchText = "";
    private boolean catalogLoading = false;
    private boolean catalogReloadQueued = false;

    // Shared cart list for selected items (can be bound to user session or passed externally)
    private static final ObservableList<Equipment> queuedCartList = FXCollections.observableArrayList();
    private ScheduledExecutorService realTimePoller;

    public static ObservableList<Equipment> getQueuedCartList() {
        return queuedCartList;
    }

    @FXML
    public void initialize() {
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
            if (colAction != null) colAction.setMinWidth(150);
            TableCells.fitColumns(tblEquipment);
        }

        queuedCartList.addListener((ListChangeListener<Equipment>) change -> {
            if (tblEquipment != null) tblEquipment.refresh();
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
                        } else if ("Checked Out".equalsIgnoreCase(currentEquipment.getStatus()) || "Pending Approval".equalsIgnoreCase(currentEquipment.getStatus())) {
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
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
                searchText = newVal == null ? "" : newVal.toLowerCase().trim();
                refilterCatalog();
            });
        }

        catalogFiltered = new FilteredList<>(masterEquipmentList, e -> true);
        catalogSorted = new SortedList<>(catalogFiltered);
        if (tblEquipment != null) {
            catalogSorted.comparatorProperty().bind(tblEquipment.comparatorProperty());
            tblEquipment.setItems(catalogSorted);
        }

        loadEquipmentData();
        startRealTimeServerPolling();
    }

    public void loadEquipmentData() {
        loadEquipmentDataSilently();
    }

    private void startRealTimeServerPolling() {
        realTimePoller = Executors.newSingleThreadScheduledExecutor();
        realTimePoller.scheduleAtFixedRate(() -> Platform.runLater(this::loadEquipmentDataSilently), 5, 5, TimeUnit.SECONDS);
    }

    public void stopPolling() {
        if (realTimePoller != null && !realTimePoller.isShutdown()) {
            realTimePoller.shutdownNow();
        }
    }

    private void loadEquipmentDataSilently() {
        if (catalogLoading) { catalogReloadQueued = true; return; }
        catalogLoading = true;
        util.LiveRefresh.background(equipmentDAO::getAllEquipment, fresh -> {
            catalogLoading = false;
            if (fresh != null) {
                if (!util.LiveRefresh.fingerprint(fresh).equals(util.LiveRefresh.fingerprint(masterEquipmentList))) {
                    masterEquipmentList.setAll(fresh);
                }
                if (tblEquipment != null) tblEquipment.refresh();
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
        loadEquipmentDataSilently();
        PauseTransition pause = new PauseTransition(Duration.millis(300));
        pause.setOnFinished(e -> {
            if (btnRefreshData != null) {
                btnRefreshData.setDisable(false);
                btnRefreshData.setText("🔄 Refresh Data");
            }
        });
        pause.play();
    }

    private void updateMetrics() {
        int total = masterEquipmentList.size();
        long available = masterEquipmentList.stream().filter(e -> "Available".equalsIgnoreCase(e.getStatus())).count();
        long checkedOut = masterEquipmentList.stream().filter(e -> "Checked Out".equalsIgnoreCase(e.getStatus()) || "Borrowed".equalsIgnoreCase(e.getStatus())).count();

        if (lblTotalUnits != null) lblTotalUnits.setText(String.valueOf(total));
        if (lblAvailableUnits != null) lblAvailableUnits.setText(String.valueOf(available));
        if (lblCheckedOutUnits != null) lblCheckedOutUnits.setText(String.valueOf(checkedOut));
    }

    private void refilterCatalog() {
        if (catalogFiltered == null) return;
        final String chip = statusChip;
        final String q = searchText;
        catalogFiltered.setPredicate(eq ->
                (chip == null || chip.equalsIgnoreCase(eq.getStatus()))
                        && (q.isEmpty() || has(eq.getName(), q) || has(eq.getCategory(), q) || has(eq.getSerialNumber(), q) || has(eq.getStatus(), q)));
    }

    private static boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    @FXML private void filterAll(ActionEvent e) { statusChip = null; refilterCatalog(); updateActiveFilterChip(btnFilterAll); }
    @FXML private void filterAvailable(ActionEvent e) { statusChip = "Available"; refilterCatalog(); updateActiveFilterChip(btnFilterAvailable); }
    @FXML private void filterRepair(ActionEvent e) { statusChip = "In Maintenance"; refilterCatalog(); updateActiveFilterChip(btnFilterMaintenance); }

    private void updateActiveFilterChip(Button activeButton) {
        Button[] chips = {btnFilterAll, btnFilterAvailable, btnFilterMaintenance};
        for (Button chip : chips) {
            if (chip != null) {
                chip.getStyleClass().remove("filter-chip-active");
                if (!chip.getStyleClass().contains("filter-chip")) chip.getStyleClass().add("filter-chip");
            }
        }
        if (activeButton != null) {
            activeButton.getStyleClass().remove("filter-chip");
            if (!activeButton.getStyleClass().contains("filter-chip-active")) activeButton.getStyleClass().add("filter-chip-active");
        }
    }

    private void openReserveDialog(Equipment item) {
        if (item == null) return;
        javafx.stage.Window owner = tblEquipment != null && tblEquipment.getScene() != null ? tblEquipment.getScene().getWindow() : null;
        if (ReserveDialog.show(owner, item)) {
            loadEquipmentDataSilently();
        }
    }
}