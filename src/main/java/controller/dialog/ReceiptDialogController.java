package controller.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class ReceiptDialogController {

    public enum PrintOption {
        COMBINED, INDIVIDUAL, NONE
    }

    private PrintOption selection = PrintOption.NONE;

    public static PrintOption show(String title, String message, Window owner) {
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initStyle(StageStyle.TRANSPARENT);

        ReceiptDialogController controller = new ReceiptDialogController();

        // Root container styling (Matches your clean FXML design)
        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e5e7eb; -fx-border-radius: 12; -fx-border-width: 1;");
        root.setPadding(new Insets(30, 40, 30, 40));

        // Blue Document Icon Circle
        StackPane iconPane = new StackPane();
        iconPane.setStyle("-fx-background-color: #dbeafe; -fx-background-radius: 50; -fx-min-width: 60; -fx-min-height: 60; -fx-max-width: 60; -fx-max-height: 60;");
        Label iconLabel = new Label("📄");
        iconLabel.setStyle("-fx-font-size: 28px;");
        iconPane.getChildren().add(iconLabel);

        // Text Content
        VBox textBox = new VBox(6);
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
        VBox.setMargin(buttonBox, new Insets(10, 0, 0, 0));

        Button btnNone = new Button("Do Not Print");
        btnNone.setPrefWidth(120);
        btnNone.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 12 8 12; -fx-background-radius: 8; -fx-cursor: hand;");
        btnNone.setOnAction(e -> {
            controller.selection = PrintOption.NONE;
            dialogStage.close();
        });

        Button btnIndividual = new Button("Individual");
        btnIndividual.setPrefWidth(120);
        btnIndividual.setStyle("-fx-background-color: #e0e7ff; -fx-text-fill: #4338ca; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 12 8 12; -fx-background-radius: 8; -fx-cursor: hand;");
        btnIndividual.setOnAction(e -> {
            controller.selection = PrintOption.INDIVIDUAL;
            dialogStage.close();
        });

        Button btnCombined = new Button("1 Combined");
        btnCombined.setPrefWidth(120);
        btnCombined.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 13px; -fx-padding: 8 12 8 12; -fx-background-radius: 8; -fx-cursor: hand;");
        btnCombined.setOnAction(e -> {
            controller.selection = PrintOption.COMBINED;
            dialogStage.close();
        });

        buttonBox.getChildren().addAll(btnNone, btnIndividual, btnCombined);

        root.getChildren().addAll(iconPane, textBox, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialogStage.setScene(scene);

        dialogStage.showAndWait();

        return controller.selection;
    }
}