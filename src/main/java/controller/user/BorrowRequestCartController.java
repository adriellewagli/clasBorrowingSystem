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

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

public class BorrowRequestCartController extends BaseController {

    @FXML private TableView<Equipment> tblCart;
    @FXML private TableColumn<Equipment, String> colCartName;
    @FXML private TableColumn<Equipment, String> colCartCategory;
    @FXML private TableColumn<Equipment, String> colCartSerial;

    @FXML private Label lblQueueCount;
    @FXML private Label lblCalculatedFee;

    @FXML private RadioButton radioStudent;
    @FXML private RadioButton radioFaculty;
    @FXML private TextField txtBorrowerName;
    @FXML private VBox boxStudentFields;
    @FXML private TextField txtStudentId;
    @FXML private ComboBox<String> txtCourse;
    @FXML private VBox boxFacultyFields;
    @FXML private TextField txtEmployeeId;
    @FXML private ComboBox<String> txtDepartment;

    @FXML private Button btn1Day;
    @FXML private Button btn3Days;
    @FXML private Button btn7Days;
    @FXML private Button btnCustomDate;
    @FXML private DatePicker dpReturnTarget;

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
    private BufferedImage capturedFrame = null;

    private final ObservableList<String> clasPrograms = FXCollections.observableArrayList(
            "AB Political Science", "BA Communication", "Bachelor of Public Administration",
            "Bachelor of Science in Computer Science", "Bachelor of Science in Entertainment and Multimedia Computing",
            "Bachelor of Science in Information System", "Bachelor of Science in Information Technology",
            "Bachelor of Science in Mathematics", "Bachelor of Science in Psychology"
    );

    @FXML
    public void initialize() {
        if (txtCourse != null) txtCourse.setItems(clasPrograms);
        if (txtDepartment != null) txtDepartment.setItems(clasPrograms);

        if (dpReturnTarget != null) {
            dpReturnTarget.getEditor().setDisable(true);
        }

        // Auto-uppercase Student ID typing
        if (txtStudentId != null) {
            txtStudentId.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(newVal.toUpperCase())) {
                    txtStudentId.setText(newVal.toUpperCase());
                }
            });
        }

        if (colCartName != null) colCartName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colCartCategory != null) colCartCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        if (colCartSerial != null) colCartSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));

        if (tblCart != null) {
            tblCart.getColumns().forEach(col -> col.setReorderable(false));
        }

        select3Days(null);
    }

    public void setCartData(ObservableList<Equipment> sharedList) {
        this.sharedCartList = sharedList;
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
            this.sharedCartList = FXCollections.observableArrayList(items);
            if (tblCart != null) {
                tblCart.setItems(this.sharedCartList);
                tblCart.refresh();
            }
            updateQueueSummary();
        }
    }

    private void updateQueueSummary() {
        int count = (sharedCartList != null) ? sharedCartList.size() : 0;
        if (lblQueueCount != null) lblQueueCount.setText(count + (count == 1 ? " Item" : " Items"));
        calculateFees();
    }

    @FXML
    private void calculateFees() {
        if (dpReturnTarget != null && dpReturnTarget.getValue() != null && sharedCartList != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), dpReturnTarget.getValue());
            if (days < 1) days = 1;

            // Update duration chip highlights dynamically based on selected date
            if (days == 1) {
                updateActiveDurationChip(btn1Day);
            } else if (days == 3) {
                updateActiveDurationChip(btn3Days);
            } else if (days == 7) {
                updateActiveDurationChip(btn7Days);
            } else {
                updateActiveDurationChip(btnCustomDate);
            }

            double totalFee = sharedCartList.size() * (days * 20.00);
            if (lblCalculatedFee != null) {
                lblCalculatedFee.setText(String.format("₱%.2f", totalFee));
            }
        }
    }

    @FXML
    private void calculateTransactionFees() {
        calculateFees();
    }

    private void updateActiveDurationChip(Button activeButton) {
        Button[] chips = {btn1Day, btn3Days, btn7Days, btnCustomDate};
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
    private void select1Day(ActionEvent e) {
        updateActiveDurationChip(btn1Day);
        if (dpReturnTarget != null) {
            dpReturnTarget.setDisable(true);
            dpReturnTarget.setValue(LocalDate.now().plusDays(1));
            calculateFees();
        }
    }

    @FXML
    private void select3Days(ActionEvent e) {
        updateActiveDurationChip(btn3Days);
        if (dpReturnTarget != null) {
            dpReturnTarget.setDisable(true);
            dpReturnTarget.setValue(LocalDate.now().plusDays(3));
            calculateFees();
        }
    }

    @FXML
    private void select7Days(ActionEvent e) {
        updateActiveDurationChip(btn7Days);
        if (dpReturnTarget != null) {
            dpReturnTarget.setDisable(true);
            dpReturnTarget.setValue(LocalDate.now().plusDays(7));
            calculateFees();
        }
    }

    @FXML
    private void selectCustomDate(ActionEvent e) {
        updateActiveDurationChip(btnCustomDate);
        if (dpReturnTarget != null) {
            dpReturnTarget.setDisable(false);
            dpReturnTarget.requestFocus();
            dpReturnTarget.show();
        }
    }

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
        if (txtEmployeeId != null) txtEmployeeId.clear();
        if (txtCourse != null) txtCourse.getSelectionModel().clearSelection();
        if (txtDepartment != null) txtDepartment.getSelectionModel().clearSelection();
        if (chkCompliance != null) chkCompliance.setSelected(false);
        handleRetakeSnap();
        select3Days(null);
    }

    @FXML
    private void handleConfirmCommit(ActionEvent event) {
        if (sharedCartList == null || sharedCartList.isEmpty()) {
            lblErrorMsg.setText("Your request queue is empty. Please add items from Catalog.");
            return;
        }

        String borrowerName = txtBorrowerName.getText().trim();
        if (borrowerName.isEmpty()) {
            lblErrorMsg.setText("Please enter full borrower name.");
            return;
        }

        boolean isStudent = radioStudent.isSelected();
        String borrowerType = isStudent ? "STUDENT" : "FACULTY";
        String borrowerId = isStudent ? txtStudentId.getText().trim() : txtEmployeeId.getText().trim();
        String dept = isStudent ? (txtCourse.getValue() != null ? txtCourse.getValue() : "") : (txtDepartment.getValue() != null ? txtDepartment.getValue() : "");

        if (borrowerId.isEmpty() || dept.isEmpty()) {
            lblErrorMsg.setText("Please complete ID number and Program/Department fields.");
            return;
        }

        if (isStudent && !borrowerId.matches("^\\d{8}-[A-Za-z0-9]$")) {
            lblErrorMsg.setText("Student Number must follow the format: 20250045-N");
            return;
        }

        if (!idCaptured) {
            lblErrorMsg.setText("ID Verification required: Please capture an ID photo.");
            return;
        }

        if (!chkCompliance.isSelected()) {
            lblErrorMsg.setText("You must accept responsibility compliance terms.");
            return;
        }

        long days = ChronoUnit.DAYS.between(LocalDate.now(), dpReturnTarget.getValue());
        double feePerItem = days * 20.00;
        byte[] snapshotBytes = getSnapshotBytes();

        UserSession session = UserSession.getInstance();
        int activeUserId = (session != null && session.getUserId() > 0) ? session.getUserId() : 1;

        List<Integer> eqIds = sharedCartList.stream().map(Equipment::getId).collect(Collectors.toList());

        boolean success = equipmentDAO.createBatchBorrowRequest(
                eqIds, borrowerName, borrowerType, borrowerId, dept,
                dpReturnTarget.getValue(), activeUserId, feePerItem, snapshotBytes
        );

        if (success) {
            closeWebcam();
            sharedCartList.clear();
            updateQueueSummary();
            clearFormInputs();
            lblErrorMsg.setText("");
            showSuccessDialog("Request Submitted", "Your batch borrow request has been logged successfully.");
        } else {
            lblErrorMsg.setText("Database error: Could not process request.");
        }
    }
}