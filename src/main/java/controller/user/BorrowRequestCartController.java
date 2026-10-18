package controller.user;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import model.Equipment;
import model.UserSession;
import util.InputRules;
import util.NameValidator;
import util.Programs;
import util.TableCells;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Imports for auto-approve and custom PDF receipt dialog
import model.BorrowedItem;
import model.Receipt;
import java.nio.file.Path;
import java.nio.file.Paths;
import controller.dialog.ReceiptDialogController;
import controller.dialog.ReceiptDialogController.PrintOption;

public class BorrowRequestCartController extends BaseController {

    private static final int DEFAULT_DAYS = 3;

    @FXML private TableView<Equipment> tblCart;
    @FXML private TableColumn<Equipment, String> colCartName;
    @FXML private TableColumn<Equipment, String> colCartCategory;
    @FXML private TableColumn<Equipment, String> colCartSerial;
    @FXML private TableColumn<Equipment, Void> colCartReturn;
    @FXML private TableColumn<Equipment, Void> colCartAction;

    @FXML private Label lblQueueCount;
    @FXML private Label lblCalculatedFee;
    @FXML private Label lblLatePenalty;

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

    @FXML private Button btn1Day;
    @FXML private Button btn3Days;
    @FXML private Button btn7Days;

    @FXML private StackPane cameraPreviewBox;
    @FXML private ImageView imgCameraPreview;
    @FXML private Label lblCameraStatus;
    @FXML private Button btnCaptureId;
    @FXML private Button btnRetakeSnap;
    @FXML private CheckBox chkCompliance;
    @FXML private Label lblErrorMsg;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private ObservableList<Equipment> sharedCartList;

    private Webcam webcam;
    private volatile boolean isCameraRunning = false;
    private boolean idCaptured = false;
    private boolean submitting = false;
    private BufferedImage capturedFrame = null;

    private final ObservableList<String> clasPrograms = FXCollections.observableArrayList(Programs.CLAS);

    @FXML
    public void initialize() {
        NameValidator.restrict(txtBorrowerName);
        if (txtCourse != null) txtCourse.setItems(clasPrograms);
        if (txtDepartment != null) txtDepartment.setItems(clasPrograms);

        InputRules.bindStudentNumber(txtStudentId, lblStudentIdError, () -> radioStudent == null || radioStudent.isSelected());

        if (colCartName != null) colCartName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colCartCategory != null) colCartCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        if (colCartSerial != null) colCartSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));

        setupReturnDateColumn();
        if (colCartAction != null) {
            colCartAction.setCellFactory(TableCells.<Equipment>dangerButton("Remove", this::removeItem));
        }

        if (tblCart != null) {
            tblCart.getColumns().forEach(col -> col.setReorderable(false));
            if (colCartReturn != null) colCartReturn.setMinWidth(190);
            TableCells.fitColumns(tblCart);
        }

        // Auto-bind to the shared catalog cart list on startup
        setCartData(controller.user.EquipmentCatalogController.getQueuedCartList());
    }

    // ------------------------------------------------------------ cart data

    public void setCartData(ObservableList<Equipment> sharedList) {
        // Fall back to the catalog's shared static cart list if none was provided
        this.sharedCartList = (sharedList != null && !sharedList.isEmpty())
                ? sharedList
                : controller.user.EquipmentCatalogController.getQueuedCartList();

        applyDefaultDates();
        if (tblCart != null) {
            tblCart.setItems(this.sharedCartList);
            tblCart.refresh();
        }
        updateQueueSummary();
    }

    public void setCartData(List<Equipment> items) {
        if (items instanceof ObservableList) {
            setCartData((ObservableList<Equipment>) items);
        } else if (items != null) {
            setCartData(FXCollections.observableArrayList(items));
        } else {
            setCartData(controller.user.EquipmentCatalogController.getQueuedCartList());
        }
    }

    private void applyDefaultDates() {
        if (sharedCartList == null) return;
        LocalDate fallback = LocalDate.now().plusDays(DEFAULT_DAYS);
        for (Equipment e : sharedCartList) {
            if (e.getReturnDate() == null || e.getReturnDate().isBefore(LocalDate.now().plusDays(1))) {
                e.setReturnDate(fallback);
            }
        }
    }

    private void updateQueueSummary() {
        int count = (sharedCartList != null) ? sharedCartList.size() : 0;
        if (lblQueueCount != null) lblQueueCount.setText(count + (count == 1 ? " Item" : " Items"));
        showFees();
    }

    /** Multiplies each item's individual borrow fee by its specific rental days. */
    private void showFees() {
        double total = 0;
        double minLate = Double.MAX_VALUE;
        double maxLate = 0;
        int n = 0;
        if (sharedCartList != null) {
            for (Equipment e : sharedCartList) {
                LocalDate due = e.getReturnDate() != null ? e.getReturnDate() : LocalDate.now().plusDays(DEFAULT_DAYS);
                long borrowDays = Math.max(1, ChronoUnit.DAYS.between(LocalDate.now(), due));

                total += e.getBorrowFee() * borrowDays;
                minLate = Math.min(minLate, e.getLateFeePerDay());
                maxLate = Math.max(maxLate, e.getLateFeePerDay());
                n++;
            }
        }
        if (lblCalculatedFee != null) lblCalculatedFee.setText(String.format("\u20B1%,.2f", total));
        if (lblLatePenalty != null) {
            if (n == 0) {
                lblLatePenalty.setText("\u2014");
            } else if (minLate == maxLate) {
                lblLatePenalty.setText(String.format("\u20B1%,.2f per day per item", maxLate));
            } else {
                lblLatePenalty.setText(String.format("\u20B1%,.2f to \u20B1%,.2f per day per item", minLate, maxLate));
            }
        }
    }

    private void removeItem(Equipment item) {
        if (item == null || sharedCartList == null) return;
        sharedCartList.removeIf(e -> e.getId() == item.getId());
        updateQueueSummary();
    }

    // ------------------------------------------------------------ per-item return date

    private void setupReturnDateColumn() {
        if (colCartReturn == null) return;

        colCartReturn.setCellFactory(col -> new TableCell<Equipment, Void>() {
            private final DatePicker picker = new DatePicker();

            {
                picker.setEditable(false);
                picker.getEditor().setDisable(true);
                picker.getEditor().setOpacity(1.0);
                picker.setMaxWidth(Double.MAX_VALUE);
                picker.setDayCellFactory(p -> new DateCell() {
                    @Override public void updateItem(LocalDate d, boolean empty) {
                        super.updateItem(d, empty);
                        setDisable(empty || d.isBefore(LocalDate.now().plusDays(1)));
                    }
                });

                // Real-time update listener for the DatePicker
                picker.valueProperty().addListener((obs, oldV, newV) -> {
                    Equipment row = currentRow();
                    if (row != null && newV != null && !newV.equals(row.getReturnDate())) {
                        row.setReturnDate(newV);
                        if (tblCart != null) {
                            tblCart.refresh();
                        }
                        showFees(); // Recalculates total fee instantly
                    }
                });
            }

            private Equipment currentRow() {
                int i = getIndex();
                if (getTableView() == null || i < 0 || i >= getTableView().getItems().size()) return null;
                return getTableView().getItems().get(i);
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                Equipment row = empty ? null : currentRow();
                if (row == null) {
                    setGraphic(null);
                    return;
                }
                picker.setValue(row.getReturnDate());
                setGraphic(picker);
            }
        });
    }

    private void setAllReturnDates(int days) {
        if (sharedCartList == null) return;
        LocalDate d = LocalDate.now().plusDays(days);
        for (Equipment e : sharedCartList) {
            e.setReturnDate(d);
        }
        if (tblCart != null) {
            tblCart.refresh();
        }
        showFees();
    }

    @FXML private void select1Day(ActionEvent e) { setAllReturnDates(1); }
    @FXML private void select3Days(ActionEvent e) { setAllReturnDates(3); }
    @FXML private void select7Days(ActionEvent e) { setAllReturnDates(7); }

    // ------------------------------------------------------------ borrower form

    @FXML
    private void handleProfileTypeChange() {
        boolean isStudent = radioStudent.isSelected();
        boxStudentFields.setVisible(isStudent);
        boxStudentFields.setManaged(isStudent);
        boxFacultyFields.setVisible(!isStudent);
        boxFacultyFields.setManaged(!isStudent);
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
                if (lblErrorMsg != null) lblErrorMsg.setText("No webcam hardware detected.");
            }
        } else {
            isCameraRunning = false;
            idCaptured = true;
            closeWebcam();

            if (lblCameraStatus != null) {
                lblCameraStatus.setText("✓ Snapshot Captured");
                lblCameraStatus.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                lblCameraStatus.setVisible(true);
            }
            btnCaptureId.setVisible(false);
            btnCaptureId.setManaged(false);
            btnRetakeSnap.setVisible(true);
            btnRetakeSnap.setManaged(true);
        }
    }

    @FXML
    private void handleRetakeSnap() {
        idCaptured = false;
        capturedFrame = null;
        closeWebcam();
        if (imgCameraPreview != null) imgCameraPreview.setImage(null);
        if (lblCameraStatus != null) {
            lblCameraStatus.setText("📷 Click capture to start camera preview");
            lblCameraStatus.setStyle("-fx-text-fill: #94a3b8;");
            lblCameraStatus.setVisible(true);
        }
        btnCaptureId.setText("📷 Start Camera / Capture");
        btnCaptureId.setVisible(true);
        btnCaptureId.setManaged(true);
        btnRetakeSnap.setVisible(false);
        btnRetakeSnap.setManaged(false);
    }

    private void closeWebcam() {
        isCameraRunning = false;
        if (webcam != null && webcam.isOpen()) webcam.close();
    }

    private byte[] getSnapshotBytes() {
        if (capturedFrame == null) return null;
        try {
            BufferedImage resized = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(capturedFrame, 0, 0, 320, 240, null);
            g2d.dispose();

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(resized, "jpg", baos);
                return baos.toByteArray();
            }
        } catch (IOException e) {
            return null;
        }
    }

    @FXML
    private void handleClearCart(ActionEvent event) {
        if (sharedCartList != null) {
            sharedCartList.clear();
        }
        updateQueueSummary();
    }

    private void clearFormInputs() {
        if (txtBorrowerName != null) txtBorrowerName.clear();
        if (txtStudentId != null) txtStudentId.clear();
        InputRules.flagStudentNumber(txtStudentId, lblStudentIdError, false);
        if (txtEmployeeId != null) txtEmployeeId.clear();
        if (txtCourse != null) txtCourse.getSelectionModel().clearSelection();
        if (txtDepartment != null) txtDepartment.getSelectionModel().clearSelection();
        if (chkCompliance != null) chkCompliance.setSelected(false);
        handleRetakeSnap();
    }

    // ------------------------------------------------------------ submit

    @FXML
    private void handleConfirmCommit(ActionEvent event) {
        if (submitting) return;
        if (sharedCartList == null || sharedCartList.isEmpty()) {
            lblErrorMsg.setText("Your request queue is empty. Please add items from Catalog.");
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
        String dept = isStudent ? (txtCourse.getValue() != null ? txtCourse.getValue() : "") : (txtDepartment.getValue() != null ? txtDepartment.getValue() : "");

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
        for (Equipment e : sharedCartList) {
            if (e.getReturnDate() == null || e.getReturnDate().isBefore(earliest)) {
                lblErrorMsg.setText("Choose a return date (tomorrow or later) for '" + e.getName() + "'.");
                return;
            }
        }

        if (!idCaptured) {
            lblErrorMsg.setText("ID Verification required: Please capture an ID photo.");
            return;
        }

        if (!chkCompliance.isSelected()) {
            lblErrorMsg.setText("You must accept responsibility compliance terms.");
            return;
        }

        // --- CUSTOM CONFIRM DIALOG PROMPT ---
        javafx.stage.Window owner = tblCart != null && tblCart.getScene() != null ? tblCart.getScene().getWindow() : null;
        boolean confirmed = controller.dialog.ConfirmDialogController.show(
                "Confirm Borrow Request",
                "Are you sure you want to submit this borrowing request for " + sharedCartList.size() + " item(s)?",
                owner
        );

        // Stop execution if user clicks Cancel
        if (!confirmed) {
            return;
        }

        byte[] snapshotBytes = getSnapshotBytes();
        UserSession session = UserSession.getInstance();
        int activeUserId = (session != null && session.getUserId() > 0) ? session.getUserId() : 1;

        Map<Integer, LocalDate> dueDates = new java.util.LinkedHashMap<>();
        for (Equipment e : sharedCartList) dueDates.putIfAbsent(e.getId(), e.getReturnDate());
        List<Integer> eqIds = new java.util.ArrayList<>(dueDates.keySet());

        submitting = true;
        javafx.scene.Node source = event != null && event.getSource() instanceof javafx.scene.Node n ? n : null;
        if (source != null) source.setDisable(true);

        String error = null;
        int approvedCount = 0;
        List<Receipt> generatedReceipts = new java.util.ArrayList<>();

        try {
            error = equipmentDAO.submitBatchBorrowRequest(
                    eqIds, dueDates, borrowerName, borrowerType, borrowerId, dept, activeUserId, snapshotBytes);

            if (error == null) {
                List<BorrowedItem> pendingRequests = equipmentDAO.getPendingRequestsForUser(activeUserId);

                for (BorrowedItem item : pendingRequests) {
                    if (eqIds.contains(item.getEquipmentId())) {
                        String approveErr = equipmentDAO.approveBorrow(item.getTransactionId(), item.getEquipmentId(), activeUserId);

                        if (approveErr == null) {
                            approvedCount++;
                            Receipt r = equipmentDAO.getReceiptForTransaction(item.getTransactionId());
                            if (r != null) {
                                generatedReceipts.add(r);
                            }
                        }
                    }
                }
            }
        } finally {
            submitting = false;
            if (source != null) source.setDisable(false);
        }

        if (error == null) {
            closeWebcam();
            sharedCartList.clear();
            updateQueueSummary();
            clearFormInputs();
            lblErrorMsg.setText("");

            String successMsg = "Success! " + approvedCount + " items were processed.\nWould you like to print your receipt?";
            // Receipt dialog handling...
            PrintOption choice = ReceiptDialogController.show(
                    "Request Auto-Approved",
                    successMsg,
                    tblCart.getScene().getWindow()
            );

            // Trigger the smooth success dialog
            controller.dialog.SuccessDialogController.show(
                    "Success!",
                    approvedCount + " equipment request(s) processed successfully.",
                    tblCart.getScene().getWindow()
            );

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

        } else {
            lblErrorMsg.setText(error);
        }
    }
}