package util;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/** The separate "borrowing details" form shown when the user presses Checkout. */
public final class CheckoutDialog {

    public record Result(String name, String type, String idNumber, String program, byte[] idSnapshot) {}

    private CheckoutDialog() {}

    /** @return the borrower's details, or null if cancelled. */
    public static Result show(Window owner, int itemCount, double totalFee) {
        Result[] result = {null};

        Label title = new Label("Borrowing Details");
        title.getStyleClass().add("modal-title");
        Label subtitle = new Label(String.format("%d item%s  \u2022  Total borrowing fee \u20B1%,.2f",
                itemCount, itemCount == 1 ? "" : "s", totalFee));
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

        Label programLabel = new Label("Academic Program");
        programLabel.getStyleClass().add("modal-label");
        ComboBox<String> cmbProgram = new ComboBox<>(FXCollections.observableArrayList(Programs.CLAS));
        cmbProgram.setPromptText("Select academic program");
        cmbProgram.setMaxWidth(Double.MAX_VALUE);
        cmbProgram.setPrefHeight(40);

        rbStudent.setOnAction(e -> swap(true, idLabel, txtId, idError, programLabel, cmbProgram));
        rbFaculty.setOnAction(e -> swap(false, idLabel, txtId, idError, programLabel, cmbProgram));

        IdSnapshotPane snapshot = new IdSnapshotPane();

        CheckBox chk = new CheckBox("I confirm the equipment will be returned on time and in good condition.");
        chk.setWrapText(true);
        chk.getStyleClass().add("modal-checkbox");

        Label message = DialogKit.message();

        Button submit = new Button("Submit Borrow Request");
        submit.getStyleClass().add("btn-success");
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, cancel, submit);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = DialogKit.form(460);
        root.getChildren().addAll(title, subtitle, typeRow,
                DialogKit.field("Full Borrower Name", txtName),
                new VBox(6, idLabel, txtId, idError),
                new VBox(6, programLabel, cmbProgram),
                DialogKit.field("ID Verification (required)", snapshot),
                chk, message, buttons);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(680);
        scroll.setStyle("-fx-background-color: #ffffff;");
        Stage stage = DialogKit.modal(owner, "Checkout", scroll);
        stage.setOnHidden(e -> snapshot.close()); // always release the camera
        cancel.setOnAction(e -> stage.close());

        submit.setOnAction(e -> {
            boolean student = rbStudent.isSelected();
            String name = txtName.getText() == null ? "" : txtName.getText().trim().replaceAll("\\s+", " ");
            String nameError = NameValidator.validate(name);
            if (nameError != null) { DialogKit.showMessage(message, nameError, false); return; }

            String id = txtId.getText() == null ? "" : txtId.getText().trim();
            String program = cmbProgram.getValue();
            if (id.isEmpty() || program == null || program.isBlank()) {
                DialogKit.showMessage(message, student
                        ? "Please enter the student number and choose a program."
                        : "Please enter the employee ID and choose a department.", false);
                return;
            }
            if (student && !InputRules.isValidStudentNumber(id)) {
                InputRules.flagStudentNumber(txtId, idError, true);
                DialogKit.showMessage(message, InputRules.STUDENT_NO_ERROR, false);
                return;
            }
            byte[] idSnapshot = snapshot.getBytes();
            if (idSnapshot == null) {
                DialogKit.showMessage(message, "ID Verification required: please capture an ID photo.", false);
                return;
            }
            if (!chk.isSelected()) {
                DialogKit.showMessage(message, "You must accept the responsibility terms.", false);
                return;
            }
            result[0] = new Result(name, student ? "STUDENT" : "FACULTY", id, program, idSnapshot);
            stage.close();
        });

        stage.showAndWait();
        return result[0];
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
    }
}