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

public class ConfirmDialogController {

    @FXML private Label lblTitle;
    @FXML private Label lblMessage;

    private Stage dialogStage;
    private boolean confirmed = false;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setDetails(String title, String message) {
        if (lblTitle != null) lblTitle.setText(title);
        if (lblMessage != null) lblMessage.setText(message);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    @FXML
    private void handleConfirm(ActionEvent event) {
        confirmed = true;
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        confirmed = false;
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Helper method to show the dialog and wait for the user's response.
     * Usage: boolean result = ConfirmDialogController.show("Title", "Message?", someNode.getScene().getWindow());
     */
    public static boolean show(String title, String message, Window owner) {
        try {
            // Adjust the FXML path if your dialogs are kept in a different folder
            FXMLLoader loader = new FXMLLoader(ConfirmDialogController.class.getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/confirm_dialog.fxml"));
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initOwner(owner);
            dialogStage.initModality(Modality.WINDOW_MODAL);

            // Keeps the background transparent so the rounded corners of your VBox show correctly
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(page);
            scene.setFill(Color.TRANSPARENT);
            dialogStage.setScene(scene);

            ConfirmDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setDetails(title, message);

            // Blocks execution until the user clicks a button and closes the modal
            dialogStage.showAndWait();

            return controller.isConfirmed();
        } catch (IOException e) {
            System.err.println("Could not load confirm_dialog.fxml");
            e.printStackTrace();
            return false;
        }
    }
}