package util;

import javafx.animation.FadeTransition;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import model.Receipt;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Digital receipt pop-up shared by the admin Audit History and the user's Active Borrows.
 * Put it as the last child of a StackPane, then call show(receipt).
 * Values wrap onto extra lines when they are long, so nothing is cut off.
 */
public class ReceiptOverlay extends StackPane {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final String LABEL_STYLE = "-fx-font-size: 12.5px; -fx-text-fill: #64748b;";
    private static final String VALUE_STYLE = "-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #0f172a;";
    private static final double LABEL_COL = 175;

    private final Label lblNumber = new Label("CBS-000000");
    private final HBox statusBox = new HBox();
    private final VBox body = new VBox(10);
    private final Label lblMessage = new Label();
    private final ScrollPane scroll = new ScrollPane(body);
    private Receipt current;

    public ReceiptOverlay() {
        getStyleClass().add("modal-backdrop");
        setVisible(false);

        Label title = new Label("Digital Receipt");
        title.getStyleClass().add("modal-title");
        lblNumber.getStyleClass().add("modal-asset-name");
        statusBox.setAlignment(Pos.CENTER_LEFT);

        VBox heading = new VBox(4, title, lblNumber, statusBox);
        HBox.setHgrow(heading, Priority.ALWAYS);

        Button x = new Button("\u2715");
        x.getStyleClass().add("modal-close-btn");
        x.setOnAction(e -> close());
        HBox top = new HBox(10, heading, x);
        top.setAlignment(Pos.TOP_LEFT);

        body.setPadding(new Insets(0, 14, 0, 0)); // keeps text clear of the scrollbar
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(430);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        lblMessage.setWrapText(true);
        lblMessage.setMaxWidth(Double.MAX_VALUE);
        lblMessage.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(lblMessage, Priority.ALWAYS);

        Button save = new Button("Save as PDF");
        save.getStyleClass().add("btn-primary");
        save.setOnAction(e -> savePdf());
        Button close = new Button("Close");
        close.getStyleClass().add("btn-secondary");
        close.setOnAction(e -> close());
        HBox footer = new HBox(10, lblMessage, save, close);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox dialog = new VBox(10, top, scroll, footer);
        dialog.getStyleClass().add("modal-dialog");
        dialog.setMaxWidth(600);
        dialog.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(dialog, Pos.CENTER);
        getChildren().add(dialog);

        setOnMouseClicked(e -> { if (e.getTarget() == this) close(); });
    }

    public void show(Receipt r) {
        current = r;
        lblMessage.setText("");
        lblNumber.setText(r.receiptNumber());

        Label pill = new Label("\u25CF  " + r.status());
        pill.getStyleClass().addAll("pill", TableCells.pillClass(r.status()));
        statusBox.getChildren().setAll(pill);

        body.getChildren().clear();

        section("TRANSACTION");
        GridPane g = grid(false);
        row(g, "Date borrowed", date(r.dateBorrowed()), false);
        row(g, "Due date", date(r.dueDate()), false);
        if (r.returnedDate() != null) row(g, "Returned on", date(r.returnedDate()), false);
        body.getChildren().add(g);

        section("BORROWER");
        g = grid(false);
        row(g, "Name", r.borrowerName(), false);
        row(g, "Type", capitalize(r.borrowerType()), false);
        row(g, "ID number", r.borrowerIdNumber(), false);
        row(g, "Program / Department", r.programOrDept(), false);
        body.getChildren().add(g);

        section("EQUIPMENT");
        g = grid(false);
        row(g, "Item", r.itemName(), false);
        row(g, "Category", r.category(), false);
        row(g, "Serial number", r.serialNumber(), false);
        row(g, "Condition when released", r.initialCondition(), false);
        body.getChildren().add(g);

        section("CHARGES");
        g = grid(true);
        row(g, "Borrowing fee", money(r.borrowingFee()), true);
        if (r.lateDays() > 0) {
            row(g, "Late penalty (" + r.lateDays() + (r.lateDays() == 1 ? " day" : " days")
                    + " x " + money(r.latePenaltyPerDay()) + ")", money(r.lateFee()), true);
        }
        Label totalLabel = new Label("TOTAL");
        totalLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Label totalValue = new Label(money(r.totalDue()));
        totalValue.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #2563eb;");
        int tr = g.getRowCount();
        g.add(totalLabel, 0, tr);
        g.add(totalValue, 1, tr);
        GridPane.setHalignment(totalValue, HPos.RIGHT);
        body.getChildren().add(g);

        section("HANDLED BY");
        g = grid(false);
        row(g, "Requested by", r.requestedBy(), false);
        row(g, "Processed by", r.processedBy(), false);
        body.getChildren().add(g);

        Label footer = new Label("System-generated digital receipt \u2022 "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
        footer.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
        footer.setPadding(new Insets(6, 0, 0, 0));
        body.getChildren().add(footer);

        scroll.setVvalue(0);
        setOpacity(0);
        setVisible(true);
        FadeTransition ft = new FadeTransition(Duration.millis(180), this);
        ft.setToValue(1);
        ft.play();
    }

    public void close() {
        setVisible(false);
    }

    // ------------------------------------------------------------------ PDF

    private void savePdf() {
        if (current == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Digital Receipt");
        fc.setInitialFileName("Receipt-" + current.receiptNumber() + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document (*.pdf)", "*.pdf"));
        File home = new File(System.getProperty("user.home", "."));
        if (home.isDirectory()) fc.setInitialDirectory(home);

        File file = fc.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }

        try {
            ReceiptPdf.write(current, file.toPath());
            lblMessage.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #16a34a;");
            lblMessage.setText("Saved: " + file.getName());
        } catch (IOException ex) {
            lblMessage.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #dc2626;");
            lblMessage.setText("Could not save the PDF: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ layout helpers

    private void section(String titleText) {
        Separator sep = new Separator();
        sep.getStyleClass().add("sidebar-divider");
        Label l = new Label(titleText);
        l.getStyleClass().add("metric-title");
        body.getChildren().addAll(sep, l);
    }

    /** Two columns: fixed-width label column, and a value column that takes the rest and wraps. */
    private GridPane grid(boolean valuesRight) {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(8);

        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(LABEL_COL);
        c0.setPrefWidth(LABEL_COL);
        c0.setMaxWidth(LABEL_COL);
        c0.setHgrow(Priority.NEVER);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(0);
        c1.setHgrow(Priority.ALWAYS);
        c1.setFillWidth(true);
        if (valuesRight) c1.setHalignment(HPos.RIGHT);

        g.getColumnConstraints().addAll(c0, c1);
        return g;
    }

    private void row(GridPane g, String label, String value, boolean right) {
        Label l = new Label(label);
        l.setStyle(LABEL_STYLE);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);

        Label v = new Label(value == null || value.isBlank() ? "\u2014" : value);
        v.setStyle(VALUE_STYLE);
        v.setWrapText(true);
        v.setMinHeight(Region.USE_PREF_SIZE); // let wrapped values grow instead of being cut off
        v.setMaxWidth(Double.MAX_VALUE);
        if (right) v.setAlignment(Pos.CENTER_RIGHT);

        int r = g.getRowCount();
        g.add(l, 0, r);
        g.add(v, 1, r);
        GridPane.setValignment(l, javafx.geometry.VPos.TOP);
        GridPane.setValignment(v, javafx.geometry.VPos.TOP);
    }

    private static String date(LocalDate d) {
        return d == null ? null : d.format(DATE_FMT);
    }

    private static String money(double amount) {
        return String.format("\u20B1%,.2f", amount);
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
