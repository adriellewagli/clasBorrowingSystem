package util;

import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.Equipment;
import model.UserSession;

import java.time.LocalDate;
import java.util.List;

/** Reserve an item for a person: pick-up date, return date and price. Works for any item, available or not. */
public final class ReserveDialog {

    private ReserveDialog() {}

    /** @return true if the reservation was saved. */
    public static boolean show(Window owner, Equipment item) {
        EquipmentDAO dao = new EquipmentDAOImpl();
        boolean[] saved = {false};

        LocalDate earliest = dao.getEarliestReservationDate(item.getId());
        List<LocalDate[]> booked = dao.getBookedRanges(item.getId());

        Label title = new Label("Reserve " + item.getName());
        title.getStyleClass().add("modal-title");

        Label subtitle = new Label("Hold this item for someone on a later date. Choose when they will pick it up and "
                + "when they will return it. Days that are already booked are greyed out. "
                + "Earliest pick-up: " + earliest + ".");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMinHeight(Region.USE_PREF_SIZE);

        ToggleGroup type = new ToggleGroup();
        RadioButton rbStudent = new RadioButton("Student");
        rbStudent.setToggleGroup(type);
        rbStudent.setSelected(true);
        RadioButton rbFaculty = new RadioButton("Faculty");
        rbFaculty.setToggleGroup(type);
        HBox typeRow = new HBox(18, rbStudent, rbFaculty);

        TextField txtName = DialogKit.textField("e.g. Juan Dela Cruz");
        NameValidator.restrict(txtName);

        Label idLabel = new Label("Student Number");
        idLabel.getStyleClass().add("modal-label");
        TextField txtId = DialogKit.textField("Format: 20250045-N");
        Label idError = new Label();
        InputRules.bindStudentNumber(txtId, idError, rbStudent::isSelected);

        ComboBox<String> cmbProgram = new ComboBox<>(FXCollections.observableArrayList(Programs.CLAS));
        cmbProgram.setPromptText("Select academic program");
        cmbProgram.setMaxWidth(Double.MAX_VALUE);
        cmbProgram.setPrefHeight(40);
        Label programLabel = new Label("Academic Program");
        programLabel.getStyleClass().add("modal-label");

        rbStudent.setOnAction(e -> swap(true, idLabel, txtId, idError, programLabel, cmbProgram));
        rbFaculty.setOnAction(e -> swap(false, idLabel, txtId, idError, programLabel, cmbProgram));

        DatePicker dpPickup = new DatePicker();
        dpPickup.setEditable(false);
        dpPickup.setMaxWidth(Double.MAX_VALUE);
        dpPickup.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                setDisable(empty || d.isBefore(earliestAllowed()) || isBooked(booked, d));
            }
        });
        DatePicker dpReturn = new DatePicker();
        dpReturn.setEditable(false);
        dpReturn.setMaxWidth(Double.MAX_VALUE);
        dpReturn.setDayCellFactory(p -> new DateCell() {
            @Override public void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                LocalDate min = dpPickup.getValue() != null ? dpPickup.getValue() : earliest;
                LocalDate block = nextBlock(booked, min);
                setDisable(empty || d.isBefore(min) || (block != null && !d.isBefore(block)));
            }
        });
        // keep the return date inside the free days that follow the pick-up date
        dpPickup.valueProperty().addListener((obs, o, n) -> {
            if (n == null) return;
            LocalDate block = nextBlock(booked, n);
            LocalDate last = block == null ? null : block.minusDays(1);
            LocalDate ret = dpReturn.getValue();
            if (ret == null || ret.isBefore(n) || (last != null && ret.isAfter(last))) {
                LocalDate fallback = n.plusDays(3);
                if (last != null && fallback.isAfter(last)) fallback = last;
                dpReturn.setValue(fallback);
            }
        });
        dpPickup.setValue(earliest);

        // ID snapshot - required, same as a normal borrow request
        IdSnapshotPane snapshot = new IdSnapshotPane();

        // The price is the item's own borrowing fee (set in Fees & Penalties). It can't be changed here.
        Label lblFeeNote = new Label(String.format(
                "Borrowing fee: \u20B1%,.2f per day  \u2022  Late fee: \u20B1%,.2f per day after the return date.",
                item.getBorrowFee(), item.getLateFeePerDay()));
        lblFeeNote.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        lblFeeNote.setWrapText(true);
        lblFeeNote.setMinHeight(Region.USE_PREF_SIZE);
        VBox feeBox = new VBox(6, lblFeeNote);

        Label message = DialogKit.message();

        Button reserve = new Button("Submit Reservation");
        reserve.getStyleClass().add("btn-primary");
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, cancel, reserve);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = DialogKit.form(460);
        root.getChildren().addAll(title, subtitle, typeRow,
                DialogKit.field("Full Borrower Name", txtName),
                new VBox(6, idLabel, txtId, idError),
                new VBox(6, programLabel, cmbProgram),
                new HBox(12,
                        grow(DialogKit.field("Pick-up date", dpPickup)),
                        grow(DialogKit.field("Return date", dpReturn))),
                feeBox,
                DialogKit.field("ID Verification (required)", snapshot),
                message, buttons);

        // the camera box makes the form tall, so let it scroll on small screens
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(680);
        scroll.setStyle("-fx-background-color: #ffffff;");
        Stage stage = DialogKit.modal(owner, "Reserve Item", scroll);
        stage.setOnHidden(e -> snapshot.close()); // always release the camera
        cancel.setOnAction(e -> stage.close());

        reserve.setOnAction(e -> {
            boolean student = rbStudent.isSelected();
            String name = txtName.getText() == null ? "" : txtName.getText().trim().replaceAll("\\s+", " ");
            String nameError = NameValidator.validate(name);
            if (nameError != null) { DialogKit.showMessage(message, nameError, false); return; }

            String id = txtId.getText() == null ? "" : txtId.getText().trim();
            String program = cmbProgram.getValue();
            if (id.isEmpty() || program == null || program.isBlank()) {
                DialogKit.showMessage(message, student
                        ? "Please enter your student number and choose a program."
                        : "Please enter your employee ID and choose a department.", false);
                return;
            }
            if (student && !InputRules.isValidStudentNumber(id)) {
                InputRules.flagStudentNumber(txtId, idError, true);
                DialogKit.showMessage(message, InputRules.STUDENT_NO_ERROR, false);
                return;
            }

            double price = item.getBorrowFee();

            byte[] idSnapshot = snapshot.getBytes();
            if (idSnapshot == null) {
                DialogKit.showMessage(message, "ID Verification required: Please take an ID snapshot.", false);
                return;
            }

            UserSession session = UserSession.getInstance();
            int uid = (session != null && session.getUserId() > 0) ? session.getUserId() : 1;

            String error = dao.createReservation(item.getId(), name, student ? "STUDENT" : "FACULTY", id, program,
                    dpPickup.getValue(), dpReturn.getValue(), uid, price, idSnapshot);
            if (error != null) { DialogKit.showMessage(message, error, false); return; }

            saved[0] = true;
            stage.close();
        });

        stage.showAndWait();
        return saved[0];
    }

    private static void swap(boolean student, Label idLabel, TextField txtId, Label idError,
                             Label programLabel, ComboBox<String> program) {
        idLabel.setText(student ? "Student Number" : "Employee ID");
        txtId.setPromptText(student ? "Format: 20250045-N" : "e.g. EMP-1002");
        programLabel.setText(student ? "Academic Program" : "Department");
        program.setPromptText(student ? "Select academic program" : "Select department");
        txtId.clear();
        InputRules.setInvalid(txtId, false);
        idError.setText("");
        idError.setVisible(false);
        idError.setManaged(false);
        if (!student) {
            // the student-number format check only applies to students
            InputRules.setInvalid(txtId, false);
        }
    }

    private static LocalDate earliestAllowed() {
        return LocalDate.now().plusDays(1);
    }

    private static boolean isBooked(List<LocalDate[]> booked, LocalDate d) {
        for (LocalDate[] w : booked) {
            if (!d.isBefore(w[0]) && !d.isAfter(w[1])) return true;
        }
        return false;
    }

    /** The first booked day after {@code after}, or null when nothing is booked later. */
    private static LocalDate nextBlock(List<LocalDate[]> booked, LocalDate after) {
        LocalDate next = null;
        for (LocalDate[] w : booked) {
            if (w[0].isAfter(after) && (next == null || w[0].isBefore(next))) next = w[0];
        }
        return next;
    }

    private static VBox grow(VBox box) {
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }
}
