package util;

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
import model.ItemFee;

import java.util.Locale;

/** Pop-up where the Super Admin edits one item's borrowing fee and late fee per day. */
public final class ItemFeeDialog {

    private static final double MAX_FEE = 100_000;

    private ItemFeeDialog() {}

    /** @return {borrowingFee, lateFeePerDay}, or null if the Super Admin cancelled. */
    public static double[] show(Window owner, ItemFee item) {
        double[][] result = {null};

        Label title = new Label("Edit fees");
        title.getStyleClass().add("modal-title");

        String serial = item.getSerialNumber().isBlank() ? "" : "  (" + item.getSerialNumber() + ")";
        Label subtitle = new Label(item.getEquipmentName() + serial);
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMinHeight(Region.USE_PREF_SIZE);

        TextField txtBorrow = moneyField(item.getBorrowFee());
        TextField txtLate = moneyField(item.getLateFeePerDay());

        Label hint = new Label("Applies to requests approved from now on. Items that are already borrowed keep "
                + "the fees they were approved with. Enter 0 for no fee.");
        hint.getStyleClass().add("field-hint-text");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);

        Label message = DialogKit.message();

        Button save = new Button("Save");
        save.getStyleClass().add("btn-primary");
        save.setDefaultButton(true);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");
        cancel.setCancelButton(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, cancel, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = DialogKit.form(440);
        root.getChildren().addAll(title, subtitle,
                DialogKit.field("Borrowing fee (\u20B1)", txtBorrow),
                DialogKit.field("Late fee per day (\u20B1)", txtLate),
                hint, message, buttons);

        Stage stage = DialogKit.modal(owner, "Edit Fees", root);
        cancel.setOnAction(e -> stage.close());

        txtBorrow.textProperty().addListener((obs, o, n) -> { InputRules.setInvalid(txtBorrow, false); message.setText(""); });
        txtLate.textProperty().addListener((obs, o, n) -> { InputRules.setInvalid(txtLate, false); message.setText(""); });

        save.setOnAction(e -> {
            Double borrow = parse(txtBorrow);
            Double late = parse(txtLate);
            if (borrow == null || late == null) {
                if (borrow == null) InputRules.setInvalid(txtBorrow, true);
                if (late == null) InputRules.setInvalid(txtLate, true);
                DialogKit.showMessage(message, "Enter an amount in both fields (0 if there is none).", false);
                return;
            }
            if (borrow > MAX_FEE || late > MAX_FEE) {
                if (borrow > MAX_FEE) InputRules.setInvalid(txtBorrow, true);
                if (late > MAX_FEE) InputRules.setInvalid(txtLate, true);
                DialogKit.showMessage(message, "A fee can't be more than " + (long) MAX_FEE + ".", false);
                return;
            }
            result[0] = new double[] {borrow, late};
            stage.close();
        });

        stage.showAndWait();
        return result[0];
    }

    /** A text field that only accepts digits with at most one dot and two decimals. */
    private static TextField moneyField(double current) {
        TextField t = DialogKit.textField("e.g. 50.00  (0 for none)");
        t.setText(String.format(Locale.ROOT, "%.2f", current));
        t.setTextFormatter(new TextFormatter<String>(c ->
                c.getControlNewText().matches("\\d{0,6}(\\.\\d{0,2})?") ? c : null));
        return t;
    }

    /** The number in the field, or null if it is empty or not a valid amount. */
    private static Double parse(TextField field) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty() || text.equals(".")) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
