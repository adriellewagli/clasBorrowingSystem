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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private boolean submitting = false; // blocks a second click while a submit is running
    private BufferedImage capturedFrame = null;

    private final ObservableList<String> clasPrograms = FXCollections.observableArrayList(Programs.CLAS);

    @FXML
    public void initialize() {
        NameValidator.restrict(txtBorrowerName);
        if (txtCourse != null) txtCourse.setItems(clasPrograms);
        if (txtDepartment != null) txtDepartment.setItems(clasPrograms);

        // Upper-cases the student number and turns the box red (with a message) when it is invalid.
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
            // the date picker and the Remove button need their full width; if the panel is narrower
            // than the columns, the queue scrolls sideways instead of cutting text ("Re...")
            if (colCartReturn != null) colCartReturn.setMinWidth(190);
            TableCells.fitColumns(tblCart);
        }

        if (lblCalculatedFee != null) lblCalculatedFee.setText("Set by admin");
    }

    // ------------------------------------------------------------ cart data

    public void setCartData(ObservableList<Equipment> sharedList) {
        this.sharedCartList = sharedList;
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
        } else {
            setCartData(FXCollections.observableArrayList(items));
        }
    }

    /** Every item starts with its own return date a few days out; the person can change each one. */
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
                picker.valueProperty().addListener((obs, oldV, newV) -> {
                    Equipment row = currentRow();
                    if (row != null && newV != null && !newV.equals(row.getReturnDate())) {
                        row.setReturnDate(newV);
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

    /** Quick buttons: give every item in the queue the same return date, then each can still be changed. */
    private void setAllReturnDates(int days) {
        if (sharedCartList == null) return;
        LocalDate d = LocalDate.now().plusDays(days);
        for (Equipment e : sharedCartList) e.setReturnDate(d);
        if (tblCart != null) tblCart.refresh();
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

        // every item needs its own valid return date
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

        byte[] snapshotBytes = getSnapshotBytes();

        UserSession session = UserSession.getInstance();
        int activeUserId = (session != null && session.getUserId() > 0) ? session.getUserId() : 1;

        // one entry per item, even if the queue somehow holds the same item twice
        Map<Integer, LocalDate> dueDates = new java.util.LinkedHashMap<>();
        for (Equipment e : sharedCartList) dueDates.putIfAbsent(e.getId(), e.getReturnDate());
        List<Integer> eqIds = new java.util.ArrayList<>(dueDates.keySet());

        submitting = true;
        javafx.scene.Node source = event != null && event.getSource() instanceof javafx.scene.Node n ? n : null;
        if (source != null) source.setDisable(true);
        String error;
        try {
            error = equipmentDAO.submitBatchBorrowRequest(
                    eqIds, dueDates, borrowerName, borrowerType, borrowerId, dept, activeUserId, snapshotBytes);
        } finally {
            submitting = false;
            if (source != null) source.setDisable(false);
        }
        boolean success = error == null;

        if (success) {
            closeWebcam();
            sharedCartList.clear();
            updateQueueSummary();
            clearFormInputs();
            lblErrorMsg.setText("");
            showSuccessDialog("Request Submitted",
                    "Your borrow request has been logged. An admin will review it and set the borrowing fee.");
        } else {
            lblErrorMsg.setText(error);
        }
    }
}
