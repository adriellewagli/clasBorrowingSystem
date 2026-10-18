package util;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.DamageReport;

/** Read-only pop-up that shows the narrative damage report written when an item was returned damaged. */
public final class DamageReportView {

    private DamageReportView() {}

    public static void show(Window owner, String itemName, DamageReport report) {
        Label title = new Label("Damage Report");
        title.getStyleClass().add("modal-title");

        Label item = new Label(itemName);
        item.getStyleClass().add("modal-asset-name");

        VBox root = DialogKit.form(480);
        root.getChildren().addAll(title, item);

        if (report == null) {
            Label none = new Label("No narrative report has been written for this item.");
            none.getStyleClass().add("page-subtitle");
            none.setWrapText(true);
            root.getChildren().add(none);
        } else {
            Label meta = new Label("Returned by " + report.borrowerName()
                    + (report.reportedOn() == null ? "" : " on " + report.reportedOn())
                    + (report.condition() == null ? "" : "  \u2022  Condition: " + report.condition())
                    + "  \u2022  Transaction #" + report.transactionId());
            meta.getStyleClass().add("page-subtitle");
            meta.setWrapText(true);
            meta.setMinHeight(Region.USE_PREF_SIZE);

            TextArea body = new TextArea(report.narrative());
            body.setEditable(false);
            body.setWrapText(true);
            body.setPrefRowCount(7);
            body.getStyleClass().add("clean-search-field");
            root.getChildren().addAll(meta, body);
        }

        Button close = new Button("Close");
        close.getStyleClass().add("btn-primary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(buttons);

        Stage stage = DialogKit.modal(owner, "Damage Report", root);
        close.setOnAction(e -> stage.close());
        stage.showAndWait();
    }
}
