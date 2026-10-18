package util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.BorrowedItem;

/**
 * "Are you sure?" pop-up shown before an admin approves a borrow or reservation.
 * The fees are not typed here: they are set per item by the Super Admin (Fees & Penalties tab)
 * and are only shown so the admin knows what the borrower will be charged.
 */
public final class ApproveBorrowDialog {

    private ApproveBorrowDialog() {}

    /** @return true if the admin confirmed the approval, false if they cancelled. */
    public static boolean show(Window owner, BorrowedItem item) {
        boolean[] approved = {false};
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
                detail("Return deadline", String.valueOf(item.getDueDate())),
                detail("Borrowing fee", money(item.getFee())),
                detail("Late fee", money(item.getLatePenaltyPerDay()) + " per day after the deadline"));
        details.setPadding(new Insets(10, 12, 10, 12));
        details.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10;");

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
        root.getChildren().addAll(title, question, details, buttons);

        Stage stage = DialogKit.modal(owner, "Approve Request", root);
        cancel.setOnAction(e -> stage.close());
        approve.setOnAction(e -> {
            approved[0] = true;
            stage.close();
        });

        stage.showAndWait();
        return approved[0];
    }

    private static String money(double v) {
        return String.format("\u20B1%,.2f", v);
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
