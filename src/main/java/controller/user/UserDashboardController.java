package controller.user;

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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.Equipment;
import model.UserSession;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

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

    // --- Search & Table FXIDs ---
    @FXML private TextField txtSearch;
    @FXML private TableView<Equipment> tblEquipment;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colStatus;
    @FXML private TableColumn<Equipment, String> colSerial;

    // --- Modal Overlay FXIDs ---
    @FXML private StackPane modalOverlay;
    @FXML private Label lblModalAssetName;
    @FXML private Label lblModalAssetDept;
    @FXML private DatePicker dpReturnTarget;
    @FXML private CheckBox chkCompliance;
    @FXML private Label lblModalError;

    // --- DATABASE DAO ---
    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();

    private final ObservableList<Equipment> masterEquipmentList = FXCollections.observableArrayList();
    private Equipment selectedEquipmentForBorrow;
    private Node myBorrowsView = null;

    @FXML
    public void initialize() {
        // 1. User Session Info Binding
        UserSession session = UserSession.getInstance();
        if (session != null) {
            if (lblUserName != null) lblUserName.setText(session.getFullName() != null ? session.getFullName() : "User");
            if (lblUserRole != null) lblUserRole.setText(session.getRole() != null ? session.getRole().toUpperCase() : "STUDENT");
            if (lblUserAvatar != null && session.getFullName() != null && !session.getFullName().isEmpty()) {
                String[] names = session.getFullName().split(" ");
                String initials = names.length > 1
                        ? ("" + names[0].charAt(0) + names[names.length - 1].charAt(0)).toUpperCase()
                        : ("" + names[0].charAt(0)).toUpperCase();
                lblUserAvatar.setText(initials);
            }
        }

        // 2. Map Standard Table Columns
        if (colName != null) colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colCategory != null) colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        if (colSerial != null) colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));

        // 3. Custom Color-Coded Cell Factory for Status Column
        if (colStatus != null) {
            colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
            colStatus.setCellFactory(column -> new TableCell<Equipment, String>() {
                @Override
                protected void updateItem(String status, boolean empty) {
                    super.updateItem(status, empty);

                    if (empty || status == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        Label badge = new Label(status.toUpperCase());
                        badge.getStyleClass().add("status-badge");

                        switch (status.toLowerCase()) {
                            case "available":
                                badge.getStyleClass().add("status-available");
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

                        setGraphic(badge);
                        setText(null);
                    }
                }
            });
        }

        // 4. Setup Row Click listener for Modal Launch
        if (tblEquipment != null) {
            tblEquipment.setOnMouseClicked(event -> {
                if (event.getClickCount() == 1) {
                    Equipment selected = tblEquipment.getSelectionModel().getSelectedItem();
                    if (selected != null && "Available".equalsIgnoreCase(selected.getStatus())) {
                        openBorrowModal(selected);
                    }
                }
            });
        }

        // 5. Setup Live Search Filter Listener
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter(newVal));
        }

        // 6. Load Initial Data from Database
        loadEquipmentData();
    }

    private void loadEquipmentData() {
        masterEquipmentList.clear();

        // FETCH DYNAMICALLY FROM MARIADB DATABASE
        List<Equipment> dbEquipment = equipmentDAO.getAllEquipment();
        masterEquipmentList.addAll(dbEquipment);

        if (tblEquipment != null) {
            tblEquipment.setItems(masterEquipmentList);
        }

        updateMetrics();
    }

    private void updateMetrics() {
        int total = masterEquipmentList.size();
        long available = masterEquipmentList.stream().filter(e -> "Available".equalsIgnoreCase(e.getStatus())).count();
        long checkedOut = masterEquipmentList.stream().filter(e -> "Checked Out".equalsIgnoreCase(e.getStatus())).count();

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

    // --- Filter Buttons ---

    @FXML
    private void filterAll(ActionEvent event) {
        if (txtSearch != null) txtSearch.clear();
        tblEquipment.setItems(masterEquipmentList);
    }

    @FXML
    private void filterAvailable(ActionEvent event) {
        ObservableList<Equipment> filtered = FXCollections.observableArrayList();
        for (Equipment eq : masterEquipmentList) {
            if ("Available".equalsIgnoreCase(eq.getStatus())) {
                filtered.add(eq);
            }
        }
        tblEquipment.setItems(filtered);
    }

    @FXML
    private void filterRepair(ActionEvent event) {
        ObservableList<Equipment> filtered = FXCollections.observableArrayList();
        for (Equipment eq : masterEquipmentList) {
            if ("In Maintenance".equalsIgnoreCase(eq.getStatus())) {
                filtered.add(eq);
            }
        }
        tblEquipment.setItems(filtered);
    }

    // --- Dynamic Navigation & View Switching ---

    @FXML
    private void handleViewCatalog(ActionEvent event) {
        updateActiveSidebarButton(btnNavCatalog);
        if (dynamicContentArea != null && catalogViewContainer != null) {
            dynamicContentArea.getChildren().setAll(catalogViewContainer);
        }
        // Refresh data when navigating back to catalog
        loadEquipmentData();
    }

    @FXML
    private void handleViewMyBorrows(ActionEvent event) {
        updateActiveSidebarButton(btnNavMyBorrows);
        try {
            if (myBorrowsView == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/users/my_borrows.fxml"));
                myBorrowsView = loader.load();
            }
            if (dynamicContentArea != null) {
                dynamicContentArea.getChildren().setAll(myBorrowsView);
            }
        } catch (IOException e) {
            System.err.println("[UserDashboardController] Error loading my_borrows.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewRequestLog(ActionEvent event) {
        updateActiveSidebarButton(btnNavRequestLog);
        System.out.println("[UserDashboard] View Request Log clicked.");
    }

    @FXML
    private void handleViewSettings(ActionEvent event) {
        updateActiveSidebarButton(btnNavSettings);
        System.out.println("[UserDashboard] View Settings clicked.");
    }

    private void updateActiveSidebarButton(Button activeButton) {
        Button[] buttons = {btnNavCatalog, btnNavMyBorrows, btnNavRequestLog, btnNavSettings};
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
    private void handleLogout(ActionEvent event) throws IOException {
        UserSession.cleanUserSession();
        navigateTo("/com/borrowclas/clasborrowingsystem/fxml/auth/login.fxml");
    }

    // --- Modal Handlers ---

    private void openBorrowModal(Equipment equipment) {
        this.selectedEquipmentForBorrow = equipment;
        if (lblModalAssetName != null) lblModalAssetName.setText(equipment.getName());
        if (lblModalAssetDept != null) lblModalAssetDept.setText("Category: " + equipment.getCategory());
        if (dpReturnTarget != null) dpReturnTarget.setValue(LocalDate.now().plusDays(3));
        if (chkCompliance != null) chkCompliance.setSelected(false);
        if (lblModalError != null) lblModalError.setText("");

        if (modalOverlay != null) {
            modalOverlay.setVisible(true);
        }
    }

    @FXML
    private void closeModal(ActionEvent event) {
        if (modalOverlay != null) {
            modalOverlay.setVisible(false);
        }
        selectedEquipmentForBorrow = null;
    }

    @FXML
    private void select3Days(ActionEvent event) {
        if (dpReturnTarget != null) dpReturnTarget.setValue(LocalDate.now().plusDays(3));
    }

    @FXML
    private void select7Days(ActionEvent event) {
        if (dpReturnTarget != null) dpReturnTarget.setValue(LocalDate.now().plusDays(7));
    }

    @FXML
    private void selectCustomDate(ActionEvent event) {
        if (dpReturnTarget != null) {
            dpReturnTarget.requestFocus();
            dpReturnTarget.show();
        }
    }

    @FXML
    private void confirmBorrowCommit(ActionEvent event) {
        if (chkCompliance != null && !chkCompliance.isSelected()) {
            if (lblModalError != null) lblModalError.setText("You must accept the terms of responsibility.");
            return;
        }

        LocalDate returnDate = dpReturnTarget != null ? dpReturnTarget.getValue() : null;
        if (returnDate == null || returnDate.isBefore(LocalDate.now())) {
            if (lblModalError != null) lblModalError.setText("Please select a valid future return date.");
            return;
        }

        if (selectedEquipmentForBorrow != null) {
            // Get the active User's ID (fallback to '1' if session isn't fully integrated yet)
            UserSession session = UserSession.getInstance();
            int currentUserId = 1;
            // If your UserSession has getUserId(), you can uncomment this:
            // if (session != null && session.getUserId() > 0) currentUserId = session.getUserId();

            // Execute the transaction in the database
            boolean success = equipmentDAO.createBorrowRequest(currentUserId, selectedEquipmentForBorrow.getId(), returnDate);

            if (success) {
                System.out.println("[Borrow Request] Successfully borrowed " + selectedEquipmentForBorrow.getName() + " until " + returnDate);
                // Reload data to show the new "Checked Out" status
                loadEquipmentData();
                closeModal(event);
            } else {
                if (lblModalError != null) lblModalError.setText("Database error: Could not process request.");
            }
        }
    }
}