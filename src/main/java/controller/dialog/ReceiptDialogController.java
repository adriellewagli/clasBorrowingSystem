package controller.dialog;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

public class ReceiptDialogController {

    public enum PrintOption {
        COMBINED, INDIVIDUAL, NONE
    }

    private PrintOption selection = PrintOption.NONE;
    private Stage dialogStage;

    public static PrintOption show(String title, String message, Window owner) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        ReceiptDialogController controller = new ReceiptDialogController();
        controller.dialogStage = dialogStage;

        // Larger, roomier container layout
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #e5e7eb; -fx-border-radius: 14; -fx-border-width: 1;");
        root.setPadding(new Insets(36, 44, 36, 44));

        // Blue Document Icon Circle
        StackPane iconPane = new StackPane();
        iconPane.setStyle("-fx-background-color: #dbeafe; -fx-background-radius: 50; -fx-min-width: 64; -fx-min-height: 64; -fx-max-width: 64; -fx-max-height: 64;");
        Label iconLabel = new Label("📄");
        iconLabel.setStyle("-fx-font-size: 30px;");
        iconPane.getChildren().add(iconLabel);

        // Text Content
        VBox textBox = new VBox(8);
        textBox.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label msgLbl = new Label(message);
        msgLbl.setWrapText(true);
        msgLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        msgLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280;");

        textBox.getChildren().addAll(titleLbl, msgLbl);

        // Action Buttons HBox
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER);
        VBox.setMargin(buttonBox, new Insets(14, 0, 0, 0));

        Button btnNone = new Button("Do Not Print");
        btnNone.setPrefWidth(125);
        btnNone.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 12 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        btnNone.setOnAction(e -> controller.closeWithChoice(PrintOption.NONE));

        Button btnIndividual = new Button("Individual");
        btnIndividual.setPrefWidth(125);
        btnIndividual.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4338ca; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 12 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        btnIndividual.setOnAction(e -> controller.closeWithChoice(PrintOption.INDIVIDUAL));

        Button btnCombined = new Button("1 Combined");
        btnCombined.setPrefWidth(125);
        btnCombined.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 10 12 10 12; -fx-background-radius: 8; -fx-cursor: hand;");
        btnCombined.setOnAction(e -> controller.closeWithChoice(PrintOption.COMBINED));

        buttonBox.getChildren().addAll(btnNone, btnIndividual, btnCombined);
        root.getChildren().addAll(iconPane, textBox, buttonBox);

        // Larger scene dimensions (480x260)
        Scene scene = new Scene(root, 480, 260);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);

        // Fade-in animation & exact screen centering
        scene.getRoot().setOpacity(0);
        dialogStage.setOnShown(e -> {
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            dialogStage.setX(screenBounds.getMinX() + (screenBounds.getWidth() - dialogStage.getWidth()) / 2.0);
            dialogStage.setY(screenBounds.getMinY() + (screenBounds.getHeight() - dialogStage.getHeight()) / 2.0);

            FadeTransition ft = new FadeTransition(Duration.millis(200), scene.getRoot());
            ft.setFromValue(0.0);
            ft.setToValue(1.0);
            ft.play();
        });

        dialogStage.showAndWait();
        return controller.selection;
    }

    private void closeWithChoice(PrintOption option) {
        this.selection = option;
        if (dialogStage == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(150), dialogStage.getScene().getRoot());
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> dialogStage.close());
        ft.play();
    }
}