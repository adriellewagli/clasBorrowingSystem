package controller.user;

import controller.BaseController;
import controller.dialog.ReceiptDialogController;
import controller.dialog.ReceiptDialogController.PrintOption;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import model.BorrowedItem;
import model.Equipment;
import model.Receipt;
import model.UserSession;
import util.IdSnapshotPane;
import util.InputRules;
import util.NameValidator;
import util.Programs;
import util.ReserveDialog;
import util.TableCells;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one "Borrow Equipment" screen: pick equipment from the list, set each item's return date and price,
 * fill in the borrower's details and submit - or use Reserve to hold an item for a later date.
 */
public class BorrowEquipmentController extends BaseController {

    private static final int DEFAULT_DAYS = 3;

    /** One selected item with its own return date and price. */
    public static final class QueueRow {
        final Equipment equipment;
        final ObjectProperty<LocalDate> returnDate = new SimpleObjectProperty<>();
        final StringProperty price = new SimpleStringProperty("");
        /** Latest return date that doesn't run into someone's reservation; null = no limit. */
        final LocalDate lastReturn;

        QueueRow(Equipment equipment, LocalDate lastReturn) {
            this.equipment = equipment;
            this.lastReturn = lastReturn;
        }
    }

    /** Kept between visits so switching tabs doesn't lose the selection. */
    private static final ObservableList<QueueRow> QUEUE = FXCollections.observableArrayList();

    /** Call on log out so the next person starts with an empty queue. */
    public static void clearQueue() {
        QUEUE.clear();
    }

    // --- equipment list ---
    @FXML private Label lblUnitSummary;
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

    // --- queue ---
    @FXML private TableView<QueueRow> tblCart;
    @FXML private TableColumn<QueueRow, String> colCartName;
    @FXML private TableColumn<QueueRow, Void> colCartReturn;
    @FXML private TableColumn<QueueRow, Void> colCartPrice;
    @FXML private TableColumn<QueueRow, Void> colCartAction;
    @FXML private Label lblQueueCount;
    @FXML private Label lblCalculatedFee;
    @FXML private Label lblLatePenalty;

    // --- borrower ---
    @FXML private RadioButton radioStudent;
    @FXML private RadioButton radioFaculty;
    @FXML private TextField txtBorrowerName;
    @FXML private VBox boxStudentFields;
    @FXML private TextField txtStudentId;
    @FXML private Label lblStudentIdError;
    @FXML private ComboBox<String> txtCourse;
    @FXML private VBox boxFacultyFields;
    @FXML private TextField txtEmployeeId;
    @FXML private ComboBox<String> txtDepartment;
    @FXML private IdSnapshotPane snapshot;
    @FXML private CheckBox chkCompliance;
    @FXML private Label lblErrorMsg;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<Equipment> masterEquipmentList = FXCollections.observableArrayList();
    private FilteredList<Equipment> catalogFiltered;
    private String statusChip = null;
    private String searchText = "";
    private boolean loading = false;
    private boolean reloadQueued = false;
    private boolean submitting = false;
    private ListChangeListener<QueueRow> queueListener;

    @FXML
    public void initialize() {
        NameValidator.restrict(txtBorrowerName);
        txtCourse.setItems(FXCollections.observableArrayList(Programs.CLAS));
        txtDepartment.setItems(FXCollections.observableArrayList(Programs.CLAS));
        InputRules.bindStudentNumber(txtStudentId, lblStudentIdError, () -> radioStudent.isSelected());

        setupEquipmentTable();
        setupQueueTable();

        txtSearch.textProperty().addListener((obs, o, n) -> {
            searchText = n == null ? "" : n.toLowerCase().trim();
            refilter();
        });

        queueListener = change -> {
            updateQueueSummary();
            tblEquipment.refresh(); // Add / Remove buttons follow the queue
        };
        QUEUE.addListener(queueListener);
        tblCart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) { // the screen was closed
                QUEUE.removeListener(queueListener);
                snapshot.close();
            }
        });

        updateQueueSummary();
        loadEquipment();
        util.LiveRefresh.watch(tblEquipment, 5, true, this::loadEquipment);
    }

    // ------------------------------------------------------------ equipment list

    private void setupEquipmentTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(TableCells.<Equipment, String>withLabelMeasure(column -> new TableCell<Equipment, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null); setGraphic(null);
                    return;
                }
                Label badge = new Label(status.toUpperCase());
                badge.getStyleClass().add("status-badge");
                switch (status.toLowerCase()) {
                    case "available" -> badge.getStyleClass().add("status-available");
                    case "pending approval", "pending" -> badge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px;");
                    case "in maintenance", "repair" -> badge.getStyleClass().add("status-maintenance");
                    case "checked out", "in use" -> badge.getStyleClass().add("status-checked-out");
                    default -> badge.getStyleClass().add("status-default");
                }
                setGraphic(badge); setText(null);
            }
        }, 24, v -> String.valueOf(v).toUpperCase(), "status-badge"));

        colAction.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue()));
        colAction.setCellFactory(col -> new TableCell<Equipment, Equipment>() {
            private final Button btnAdd = new Button();
            private final Button btnReserve = new Button("Reserve");
            private final HBox box = new HBox(8, btnAdd, btnReserve);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnAdd.setPrefHeight(32);
                btnAdd.setMinWidth(96);
                btnReserve.setPrefHeight(32);
                btnReserve.setMinWidth(84);
                btnReserve.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #2563eb; -fx-border-color: #2563eb; -fx-border-radius: 20px; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
            }

            @Override
            protected void updateItem(Equipment eq, boolean empty) {
                super.updateItem(eq, empty);
                if (empty || eq == null) { setGraphic(null); return; }

                boolean available = "Available".equalsIgnoreCase(eq.getStatus());
                boolean maintenance = "In Maintenance".equalsIgnoreCase(eq.getStatus());

                btnAdd.setVisible(available);
                btnAdd.setManaged(available);
                if (available) {
                    boolean queued = isQueued(eq);
                    btnAdd.setText(queued ? "✕ Remove" : "+ Add");
                    btnAdd.setStyle(queued
                            ? "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;"
                            : "-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20px; -fx-cursor: hand;");
                    btnAdd.setOnAction(e -> toggleQueued(eq));
                }

                // every item that isn't in maintenance can be reserved - available or not
                btnReserve.setVisible(!maintenance);
                btnReserve.setManaged(!maintenance);
                btnReserve.setOnAction(e -> openReserveDialog(eq));

                if (maintenance) {
                    Label unavailable = new Label("Unavailable");
                    unavailable.setStyle("-fx-text-fill: #94a3b8;");
                    setGraphic(unavailable);
                } else {
                    setGraphic(box);
                }
            }
        });

        tblEquipment.getColumns().forEach(c -> c.setReorderable(false));
        colAction.setMinWidth(230);
        TableCells.fitColumns(tblEquipment);

        catalogFiltered = new FilteredList<>(masterEquipmentList, e -> true);
        SortedList<Equipment> sorted = new SortedList<>(catalogFiltered);
        sorted.comparatorProperty().bind(tblEquipment.comparatorProperty());
        util.TablePager.install(tblEquipment, catalogFiltered, sorted);
    }

    private static boolean isQueued(Equipment eq) {
        return QUEUE.stream().anyMatch(r -> r.equipment.getId() == eq.getId());
    }

    private void toggleQueued(Equipment eq) {
        if (isQueued(eq)) {
            QUEUE.removeIf(r -> r.equipment.getId() == eq.getId());
            return;
        }

        // reservations that start soon limit how long the item can be borrowed right now
        LocalDate today = LocalDate.now();
        LocalDate last = null;
        for (LocalDate[] w : equipmentDAO.getBookedRanges(eq.getId())) {
            if (!w[0].isAfter(today)) {
                showErrorDialog("Not available", "'" + eq.getName() + "' is no longer available. The list will refresh.");
                loadEquipment();
                return;
            }
            LocalDate before = w[0].minusDays(1);
            if (last == null || before.isBefore(last)) last = before;
        }
        if (last != null && last.isBefore(today.plusDays(1))) {
            showErrorDialog("Reserved soon", "'" + eq.getName() + "' is reserved from tomorrow, so it can't be borrowed now. "
                    + "Use Reserve to book it for a later date.");
            return;
        }

        QueueRow row = new QueueRow(eq, last);
        LocalDate def = today.plusDays(DEFAULT_DAYS);
        row.returnDate.set(last != null && def.isAfter(last) ? last : def);
        QUEUE.add(row);
    }

    private void openReserveDialog(Equipment item) {
        javafx.stage.Window owner = tblEquipment.getScene() != null ? tblEquipment.getScene().getWindow() : null;
        if (ReserveDialog.show(owner, item)) {
            loadEquipment();
            showSuccessDialog("Reservation Submitted",
                    "'" + item.getName() + "' is reserved for the dates you chose. "
                            + "An admin approves it (hands it over) on the pick-up day. "
                            + "You can see or cancel it under My Requests.");
        }
    }

    private void loadEquipment() {
        if (loading) { reloadQueued = true; return; }
        loading = true;
        util.LiveRefresh.background(equipmentDAO::getAllEquipment, fresh -> {
            loading = false;
            if (fresh != null) {
                java.util.List<model.Equipment> visible = fresh.stream().filter(e -> !e.isDisabled()).toList();
                if (!util.LiveRefresh.fingerprint(visible).equals(util.LiveRefresh.fingerprint(masterEquipmentList))) {
                    masterEquipmentList.setAll(visible);
                }
                tblEquipment.refresh();
                long available = masterEquipmentList.stream().filter(e -> "Available".equalsIgnoreCase(e.getStatus())).count();
                lblUnitSummary.setText(available + " available of " + masterEquipmentList.size());
            }
            if (reloadQueued) {
                reloadQueued = false;
                loadEquipment();
            }
        });
    }

    @FXML
    private void handleManualRefresh(ActionEvent event) {
        loadEquipment();
    }

    private void refilter() {
        final String chip = statusChip;
        final String q = searchText;
        catalogFiltered.setPredicate(eq ->
                (chip == null || chip.equalsIgnoreCase(eq.getStatus()))
                        && (q.isEmpty() || has(eq.getName(), q) || has(eq.getCategory(), q)
                        || has(eq.getSerialNumber(), q) || has(eq.getStatus(), q)));
    }

    private static boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    @FXML private void filterAll(ActionEvent e) { statusChip = null; refilter(); markChip(btnFilterAll); }
    @FXML private void filterAvailable(ActionEvent e) { statusChip = "Available"; refilter(); markChip(btnFilterAvailable); }
    @FXML private void filterRepair(ActionEvent e) { statusChip = "In Maintenance"; refilter(); markChip(btnFilterMaintenance); }

    private void markChip(Button active) {
        for (Button chip : new Button[]{btnFilterAll, btnFilterAvailable, btnFilterMaintenance}) {
            chip.getStyleClass().remove("filter-chip-active");
            if (!chip.getStyleClass().contains("filter-chip")) chip.getStyleClass().add("filter-chip");
        }
        active.getStyleClass().remove("filter-chip");
        active.getStyleClass().add("filter-chip-active");
    }

    // ------------------------------------------------------------ queue (per-item return date + price)

    private void setupQueueTable() {
        colCartName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().equipment.getName()));

        colCartReturn.setCellFactory(col -> new TableCell<QueueRow, Void>() {
            private final DatePicker picker = new DatePicker();

            {
                picker.setEditable(false);
                picker.getEditor().setDisable(true);
                picker.getEditor().setOpacity(1.0);
                picker.setMaxWidth(Double.MAX_VALUE);
                picker.setDayCellFactory(p -> new DateCell() {
                    @Override public void updateItem(LocalDate d, boolean empty) {
                        super.updateItem(d, empty);
                        QueueRow row = currentRow();
                        boolean tooLate = row != null && row.lastReturn != null && d != null && d.isAfter(row.lastReturn);
                        setDisable(empty || d.isBefore(LocalDate.now().plusDays(1)) || tooLate);
                    }
                });
                picker.valueProperty().addListener((obs, o, n) -> {
                    QueueRow row = currentRow();
                    if (row != null && n != null && !n.equals(row.returnDate.get())) {
                        row.returnDate.set(n);
                    }
                });
            }

            private QueueRow currentRow() {
                int i = getIndex();
                if (getTableView() == null || i < 0 || i >= getTableView().getItems().size()) return null;
                return getTableView().getItems().get(i);
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                QueueRow row = empty ? null : currentRow();
                if (row == null) { setGraphic(null); return; }
                picker.setValue(row.returnDate.get());
                setGraphic(picker);
            }
        });

        colCartPrice.setCellFactory(col -> new TableCell<QueueRow, Void>() {
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                int i = getIndex();
                if (empty || getTableView() == null || i < 0 || i >= getTableView().getItems().size()) {
                    setText(null);
                    return;
                }
                setText(String.format("\u20B1%,.2f", getTableView().getItems().get(i).equipment.getBorrowFee()));
            }
        });

        colCartAction.setCellFactory(TableCells.<QueueRow>dangerButton("Remove", this::removeRow));

        tblCart.getColumns().forEach(c -> c.setReorderable(false));
        colCartReturn.setMinWidth(180);
        colCartPrice.setMinWidth(120);
        TableCells.fitColumns(tblCart);
        tblCart.setItems(QUEUE);
    }

    private void removeRow(QueueRow row) {
        if (row != null) QUEUE.remove(row);
    }

    private static double parsePrice(String text) {
        try {
            return (text == null || text.isBlank()) ? Double.NaN : Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private void updateQueueSummary() {
        int count = QUEUE.size();
        lblQueueCount.setText(count + (count == 1 ? " Item" : " Items"));

        double total = 0;
        double minLate = Double.MAX_VALUE;
        double maxLate = 0;
        for (QueueRow r : QUEUE) {
            total += r.equipment.getBorrowFee();
            minLate = Math.min(minLate, r.equipment.getLateFeePerDay());
            maxLate = Math.max(maxLate, r.equipment.getLateFeePerDay());
        }
        lblCalculatedFee.setText(String.format("\u20B1%,.2f", total));
        if (count == 0) {
            lblLatePenalty.setText("\u2014");
        } else if (minLate == maxLate) {
            lblLatePenalty.setText(String.format("\u20B1%,.2f per day per item", maxLate));
        } else {
            lblLatePenalty.setText(String.format("\u20B1%,.2f to \u20B1%,.2f per day per item", minLate, maxLate));
        }
    }

    private void setAllReturnDates(int days) {
        LocalDate d = LocalDate.now().plusDays(days);
        for (QueueRow r : QUEUE) {
            r.returnDate.set(r.lastReturn != null && d.isAfter(r.lastReturn) ? r.lastReturn : d);
        }
        tblCart.refresh();
    }

    @FXML private void select1Day(ActionEvent e) { setAllReturnDates(1); }
    @FXML private void select3Days(ActionEvent e) { setAllReturnDates(3); }
    @FXML private void select7Days(ActionEvent e) { setAllReturnDates(7); }

    @FXML
    private void handleClearCart(ActionEvent event) {
        QUEUE.clear();
    }

    // ------------------------------------------------------------ borrower form

    @FXML
    private void handleProfileTypeChange() {
        boolean isStudent = radioStudent.isSelected();
        boxStudentFields.setVisible(isStudent);
        boxStudentFields.setManaged(isStudent);
        boxFacultyFields.setVisible(!isStudent);
        boxFacultyFields.setManaged(!isStudent);
    }

    private void clearFormInputs() {
        txtBorrowerName.clear();
        txtStudentId.clear();
        InputRules.flagStudentNumber(txtStudentId, lblStudentIdError, false);
        txtEmployeeId.clear();
        txtCourse.getSelectionModel().clearSelection();
        txtDepartment.getSelectionModel().clearSelection();
        chkCompliance.setSelected(false);
        snapshot.reset();
    }

    // ------------------------------------------------------------ submit

    @FXML
    private void handleConfirmCommit(ActionEvent event) {
        if (submitting) return;
        if (QUEUE.isEmpty()) {
            lblErrorMsg.setText("Your queue is empty. Click + Add on an available item first.");
            return;
        }

        String borrowerName = txtBorrowerName.getText().trim().replaceAll("\\s+", " ");
        String nameError = NameValidator.validate(borrowerName);
        if (nameError != null) {
            lblErrorMsg.setText(nameError);
            return;
        }

        boolean isStudent = radioStudent.isSelected();
        String borrowerType = isStudent ? "STUDENT" : "FACULTY";
        String borrowerId = isStudent ? txtStudentId.getText().trim() : txtEmployeeId.getText().trim();
        String dept = isStudent
                ? (txtCourse.getValue() != null ? txtCourse.getValue() : "")
                : (txtDepartment.getValue() != null ? txtDepartment.getValue() : "");

        if (borrowerId.isEmpty() || dept.isEmpty()) {
            if (isStudent && borrowerId.isEmpty()) InputRules.flagStudentNumber(txtStudentId, lblStudentIdError, true);
            lblErrorMsg.setText("Please complete ID number and Program/Department fields.");
            return;
        }
        if (isStudent && !InputRules.isValidStudentNumber(borrowerId)) {
            InputRules.flagStudentNumber(txtStudentId, lblStudentIdError, true);
            txtStudentId.requestFocus();
            lblErrorMsg.setText(InputRules.STUDENT_NO_ERROR);
            return;
        }

        LocalDate earliest = LocalDate.now().plusDays(1);
        Map<Integer, LocalDate> dueDates = new LinkedHashMap<>();
        Map<Integer, Double> prices = new LinkedHashMap<>();
        for (QueueRow r : QUEUE) {
            String name = r.equipment.getName();
            LocalDate due = r.returnDate.get();
            if (due == null || due.isBefore(earliest)) {
                lblErrorMsg.setText("Choose a return date (tomorrow or later) for '" + name + "'.");
                return;
            }
            dueDates.putIfAbsent(r.equipment.getId(), due);
            prices.putIfAbsent(r.equipment.getId(), r.equipment.getBorrowFee());
        }

        byte[] snapshotBytes = snapshot.getBytes();
        if (snapshotBytes == null) {
            lblErrorMsg.setText("ID Verification required: Please capture an ID photo.");
            return;
        }
        if (!chkCompliance.isSelected()) {
            lblErrorMsg.setText("You must accept responsibility compliance terms.");
            return;
        }

        javafx.stage.Window owner = tblCart.getScene() != null ? tblCart.getScene().getWindow() : null;
        boolean confirmed = controller.dialog.ConfirmDialogController.show(
                "Confirm Borrow Request",
                "Are you sure you want to submit this borrowing request for " + dueDates.size() + " item(s)?",
                owner);
        if (!confirmed) return;

        UserSession session = UserSession.getInstance();
        int activeUserId = (session != null && session.getUserId() > 0) ? session.getUserId() : 1;
        List<Integer> eqIds = new ArrayList<>(dueDates.keySet());

        submitting = true;
        javafx.scene.Node source = event != null && event.getSource() instanceof javafx.scene.Node n ? n : null;
        if (source != null) source.setDisable(true);

        String error;
        int approvedCount = 0;
        List<Receipt> generatedReceipts = new ArrayList<>();

        try {
            error = equipmentDAO.submitBatchBorrowRequest(eqIds, dueDates, prices, borrowerName, borrowerType,
                    borrowerId, dept, activeUserId, snapshotBytes);

            if (error == null) {
                for (BorrowedItem item : equipmentDAO.getPendingRequestsForUser(activeUserId)) {
                    if ("Reservation".equalsIgnoreCase(item.getRequestType())) continue; // reservations wait for pick-up day
                    if (!eqIds.contains(item.getEquipmentId())) continue;
                    if (equipmentDAO.approveBorrow(item.getTransactionId(), item.getEquipmentId(), activeUserId) == null) {
                        approvedCount++;
                        Receipt r = equipmentDAO.getReceiptForTransaction(item.getTransactionId());
                        if (r != null) generatedReceipts.add(r);
                    }
                }
            }
        } finally {
            submitting = false;
            if (source != null) source.setDisable(false);
        }

        if (error != null) {
            lblErrorMsg.setText(error);
            return;
        }

        QUEUE.clear();
        clearFormInputs();
        lblErrorMsg.setText("");
        loadEquipment();

        javafx.stage.Window win = tblCart.getScene() != null ? tblCart.getScene().getWindow() : null;
        PrintOption choice = ReceiptDialogController.show(
                "Request Auto-Approved",
                "Success! " + approvedCount + " items were processed.\nWould you like to print your receipt?",
                win);
        controller.dialog.SuccessDialogController.show(
                "Success!", approvedCount + " equipment request(s) processed successfully.", win);

        if (choice == PrintOption.COMBINED && !generatedReceipts.isEmpty()) {
            try {
                Path path = Paths.get(System.getProperty("user.home"), "Downloads", "Combined_Receipt_" + System.currentTimeMillis() + ".pdf");
                util.CombinedReceiptPdf.write(generatedReceipts, path);
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
            } catch (IOException e) {
                System.err.println("Failed to open combined PDF: " + e.getMessage());
            }
        } else if (choice == PrintOption.INDIVIDUAL) {
            for (Receipt r : generatedReceipts) {
                try {
                    Path path = Paths.get(System.getProperty("user.home"), "Downloads", "Receipt_" + r.receiptNumber() + ".pdf");
                    util.ReceiptPdf.write(r, path);
                    if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
                } catch (IOException e) {
                    System.err.println("Failed to open individual PDF: " + e.getMessage());
                }
            }
        }
    }
}
