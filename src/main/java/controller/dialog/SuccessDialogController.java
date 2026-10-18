package controller.dialog;

import javafx.animation.FadeTransition;
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

public class SuccessDialogController {

    @FXML private Label lblTitle;
    @FXML private Label lblMessage;

    private Stage dialogStage;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setDialogDetails(String title, String message) {
        if (lblTitle != null) lblTitle.setText(title);
        if (lblMessage != null) lblMessage.setText(message);
    }

    @FXML
    private void handleClose() {
        if (dialogStage == null && lblTitle != null && lblTitle.getScene() != null) {
            dialogStage = (Stage) lblTitle.getScene().getWindow();
        }

        if (dialogStage != null) {
            FadeTransition ft = new FadeTransition(Duration.millis(150), dialogStage.getScene().getRoot());
            ft.setFromValue(1.0);
            ft.setToValue(0.0);
            ft.setOnFinished(e -> dialogStage.close());
            ft.play();
        } else if (lblTitle != null && lblTitle.getScene() != null) {
            // Fallback immediate close if stage ref wasn't set
            ((Stage) lblTitle.getScene().getWindow()).close();
        }
    }

    public static void show(String title, String message, Window owner) {
        try {
            // Try relative path first (same folder as controller)
            URL fxmlUrl = SuccessDialogController.class.getResource("success_dialog.fxml");

            // If not found, try absolute path based on standard package structure
            if (fxmlUrl == null) {
                fxmlUrl = SuccessDialogController.class.getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/success_dialog.fxml");
            }

            if (fxmlUrl == null) {
                System.err.println("CRITICAL ERROR: 'success_dialog.fxml' could not be found anywhere by the class loader!");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initOwner(owner);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initStyle(StageStyle.TRANSPARENT);

            // Roomy, well-proportioned dimensions
            Scene scene = new Scene(page, 460, 240);
            scene.setFill(Color.TRANSPARENT);
            dialogStage.setScene(scene);

            SuccessDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setDialogDetails(title, message);

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

        } catch (Exception e) {
            System.err.println("Failed to load success_dialog.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }
}