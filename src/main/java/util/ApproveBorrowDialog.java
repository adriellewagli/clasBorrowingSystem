package util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.BorrowedItem;

import java.util.OptionalDouble;

/**
 * "Are you sure?" pop-up shown before an admin approves a borrow or reservation.
 * The admin also types how much this item costs to borrow (there is no fixed price any more).
 */
public final class ApproveBorrowDialog {

    private static final double MAX_FEE = 100_000;

    private ApproveBorrowDialog() {}

    /** @return the fee the admin entered, or empty if they cancelled. */
    public static OptionalDouble show(Window owner, BorrowedItem item, double latePenaltyPerDay) {
        double[] result = {Double.NaN};
        boolean reservation = "Reservation".equalsIgnoreCase(item.getRequestType());

        Label title = new Label(reservation ? "Approve this reservation?" : "Allow this borrowing?");
        title.getStyleClass().add("modal-title");

        Label question = new Label("Are you sure you want to allow " + item.getBorrowerName() + " to "
                + (reservation ? "reserve and borrow " : "borrow ") + item.getEquipmentName() + "? "
                + "The item will be marked as checked out.");
        question.getStyleClass().add("page-subtitle");
        question.setWrapText(true);
        question.setMinHeight(Region.USE_PREF_SIZE);

        VBox details = new VBox(4,
                detail("Item", item.getEquipmentName() + (item.getSerialNumber() == null || item.getSerialNumber().isBlank()
                        ? "" : "  (" + item.getSerialNumber() + ")")),
                detail("Borrower", item.getBorrowerName()
                        + (item.getBorrowerIdNumber().isEmpty() ? "" : "  \u2022  " + item.getBorrowerIdNumber())),
                detail(reservation ? "Pick-up date" : "Requested on", String.valueOf(item.getBorrowDate())),
                detail("Return deadline", String.valueOf(item.getDueDate())));
        details.setPadding(new Insets(10, 12, 10, 12));
        details.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10;");

        TextField txtFee = DialogKit.textField("e.g. 50.00  (enter 0 if free)");
        // digits with at most one dot and two decimals
        txtFee.setTextFormatter(new TextFormatter<String>(c ->
                c.getControlNewText().matches("\\d{0,6}(\\.\\d{0,2})?") ? c : null));

        Label feeHint = new Label("Set the borrowing fee for this item. Late returns are charged \u20B1"
                + String.format("%,.2f", latePenaltyPerDay) + " per day after the deadline.");
        feeHint.getStyleClass().add("field-hint-text");
        feeHint.setWrapText(true);
        feeHint.setMinHeight(Region.USE_PREF_SIZE);

        Label message = DialogKit.message();

        Button approve = new Button("Yes, Approve");
        approve.getStyleClass().add("btn-primary");
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");
        cancel.setCancelButton(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, cancel, approve);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = DialogKit.form(460);
        root.getChildren().addAll(title, question, details,
                DialogKit.field("Borrowing fee (\u20B1)", txtFee), feeHint, message, buttons);

        Stage stage = DialogKit.modal(owner, "Approve Request", root);
        cancel.setOnAction(e -> stage.close());

        approve.setOnAction(e -> {
            String text = txtFee.getText() == null ? "" : txtFee.getText().trim();
            if (text.isEmpty() || text.equals(".")) {
                InputRules.setInvalid(txtFee, true);
                DialogKit.showMessage(message, "Enter the borrowing fee for this item (0 if there is none).", false);
                return;
            }
            double fee;
            try {
                fee = Double.parseDouble(text);
            } catch (NumberFormatException ex) {
                InputRules.setInvalid(txtFee, true);
                DialogKit.showMessage(message, "That fee isn't a valid amount.", false);
                return;
            }
            if (fee < 0 || fee > MAX_FEE) {
                InputRules.setInvalid(txtFee, true);
                DialogKit.showMessage(message, "The fee must be between 0 and " + (long) MAX_FEE + ".", false);
                return;
            }
            result[0] = fee;
            stage.close();
        });
        txtFee.textProperty().addListener((obs, o, n) -> {
            InputRules.setInvalid(txtFee, false);
            message.setText("");
        });

        stage.showAndWait();
        return Double.isNaN(result[0]) ? OptionalDouble.empty() : OptionalDouble.of(result[0]);
    }

    private static HBox detail(String label, String value) {
        Label l = new Label(label);
        l.setMinWidth(110);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #64748b;");
        Label v = new Label(value);
        v.setWrapText(true);
        v.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");
        HBox row = new HBox(8, l, v);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }
}
