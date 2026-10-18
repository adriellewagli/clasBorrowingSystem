package controller.dialog;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.net.URL;

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
        closeWithFade();
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        confirmed = false;
        closeWithFade();
    }

    private void closeWithFade() {
        if (dialogStage == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(150), dialogStage.getScene().getRoot());
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> dialogStage.close());
        ft.play();
    }

    public static boolean show(String title, String message, Window owner) {
        try {
            // Try relative path first (same folder as controller)
            URL fxmlUrl = ConfirmDialogController.class.getResource("confirm_dialog.fxml");

            // If not found, try absolute path based on standard package structure
            if (fxmlUrl == null) {
                fxmlUrl = ConfirmDialogController.class.getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/confirm_dialog.fxml");
            }

            if (fxmlUrl == null) {
                System.err.println("CRITICAL ERROR: 'confirm_dialog.fxml' could not be found anywhere by the class loader!");
                System.err.println("Please ensure the file exists in the dialog folder and do a 'Rebuild Project'.");
                return false;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initOwner(owner);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initStyle(StageStyle.TRANSPARENT);

            // Make it larger and roomier
            Scene scene = new Scene(page, 460, 240);
            scene.setFill(Color.TRANSPARENT);
            dialogStage.setScene(scene);

            ConfirmDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setDetails(title, message);

            // Fade-in animation on show
            scene.getRoot().setOpacity(0);
            dialogStage.setOnShown(e -> {
                // Perfect absolute centering on the screen
                Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
                dialogStage.setX(screenBounds.getMinX() + (screenBounds.getWidth() - dialogStage.getWidth()) / 2.0);
                dialogStage.setY(screenBounds.getMinY() + (screenBounds.getHeight() - dialogStage.getHeight()) / 2.0);

                FadeTransition ft = new FadeTransition(Duration.millis(200), scene.getRoot());
                ft.setFromValue(0.0);
                ft.setToValue(1.0);
                ft.play();
            });

            dialogStage.showAndWait();

            return controller.isConfirmed();
        } catch (Exception e) {
            System.err.println("Failed to load confirm_dialog.fxml: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}