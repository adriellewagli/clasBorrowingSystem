package util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.BorrowedItem;
import model.BorrowerDetails;

import java.io.ByteArrayInputStream;

/** Pop-up for the Super Admin: who borrowed an item, with the ID photo that was taken at the time. */
public final class BorrowerDetailsDialog {

    private BorrowerDetailsDialog() {}

    public static void show(Window owner, BorrowedItem item, BorrowerDetails d) {
        Label title = new Label("Borrower Details");
        title.getStyleClass().add("modal-title");

        String serial = (item.getSerialNumber() == null || item.getSerialNumber().isBlank())
                ? "" : "  (" + item.getSerialNumber() + ")";
        Label subtitle = new Label("Transaction #" + d.getTransactionId() + "  \u2022  " + item.getEquipmentName() + serial);
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMinHeight(Region.USE_PREF_SIZE);

        boolean student = d.isStudent();
        VBox details = new VBox(4,
                detail("Full name", d.getBorrowerName()),
                detail("Borrower type", d.getTypeLabel()),
                detail(student ? "Student number" : "Employee ID", d.getBorrowerIdNumber()),
                detail(student ? "Program" : "Department", d.getProgramOrDept()),
                detail("Requested by", d.getRequestedBy()),
                detail("Borrowed", String.valueOf(item.getBorrowDate())),
                detail("Due", String.valueOf(item.getDueDate())),
                detail("Status", String.valueOf(item.getStatus())));
        details.setPadding(new Insets(10, 12, 10, 12));
        details.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10;");

        Label photoTitle = new Label("ID photo");
        photoTitle.getStyleClass().add("modal-label");
        VBox photoSection = new VBox(6, photoTitle, photo(d));

        Button close = new Button("Close");
        close.getStyleClass().add("btn-primary");
        close.setCancelButton(true);
        close.setDefaultButton(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = DialogKit.form(480);
        root.getChildren().addAll(title, subtitle, details, photoSection, buttons);

        Stage stage = DialogKit.modal(owner, "Borrower Details", root);
        close.setOnAction(e -> stage.close());
        stage.showAndWait();
    }

    /** The ID photo, or a short note when there is none (reservations don't have one) or it can't be shown. */
    private static Node photo(BorrowerDetails d) {
        if (!d.hasIdPhoto()) {
            return note("No ID photo was saved for this transaction (reservations don't include one).");
        }
        Image image = new Image(new ByteArrayInputStream(d.getIdSnapshot()));
        if (image.isError()) {
            return note("The ID photo could not be displayed.");
        }
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        // the photo is stored small (320 px wide): show it at its own size rather than blowing it up
        view.setFitWidth(Math.min(image.getWidth(), 390));

        StackPane box = new StackPane(view);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10;");
        return box;
    }

    private static Label note(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #64748b;");
        return l;
    }

    private static HBox detail(String label, String value) {
        Label l = new Label(label);
        l.setMinWidth(120);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #64748b;");
        Label v = new Label(value);
        v.setWrapText(true);
        v.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");
        HBox row = new HBox(8, l, v);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }
}