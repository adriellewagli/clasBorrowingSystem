package controller.dialog;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;

public class ReceiptDialogController {

    public enum PrintOption {
        COMBINED, INDIVIDUAL, NONE
    }

    @FXML private Label lblTitle;
    @FXML private Label lblMessage;

    private Stage dialogStage;
    private PrintOption selection = PrintOption.NONE;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setDetails(String title, String message) {
        if (lblTitle != null) lblTitle.setText(title);
        if (lblMessage != null) lblMessage.setText(message);
    }

    public PrintOption getSelection() {
        return selection;
    }

    @FXML
    private void handleCombined(ActionEvent event) {
        selection = PrintOption.COMBINED;
        if (dialogStage != null) dialogStage.close();
    }

    @FXML
    private void handleIndividual(ActionEvent event) {
        selection = PrintOption.INDIVIDUAL;
        if (dialogStage != null) dialogStage.close();
    }

    @FXML
    private void handleNone(ActionEvent event) {
        selection = PrintOption.NONE;
        if (dialogStage != null) dialogStage.close();
    }

    public static PrintOption show(String title, String message, Window owner) {
        try {
            FXMLLoader loader = new FXMLLoader(ReceiptDialogController.class.getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/receipt_dialog.fxml"));
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initOwner(owner);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initStyle(StageStyle.TRANSPARENT);

            Scene scene = new Scene(page);
            scene.setFill(Color.TRANSPARENT);
            dialogStage.setScene(scene);

            ReceiptDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setDetails(title, message);

            dialogStage.showAndWait();

            return controller.getSelection();
        } catch (IOException e) {
            System.err.println("Could not load receipt_dialog.fxml");
            e.printStackTrace();
            return PrintOption.NONE;
        }
    }
}