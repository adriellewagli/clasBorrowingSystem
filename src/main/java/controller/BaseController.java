package controller;

import controller.dialog.SuccessDialogController;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;

public abstract class BaseController {

    /**
     * Primary single-scene navigation method.
     */
    protected <T> T navigateTo(String fxmlPath) throws IOException {
        return MainLayoutController.setView(fxmlPath);
    }

    /**
     * Overload for backward compatibility with existing controller calls.
     */
    protected <T> T navigateTo(ActionEvent event, String fxmlPath, String title) throws IOException {
        return navigateTo(fxmlPath);
    }

    protected void playFadeIn(Node node) {
        if (node == null) return;
        node.setOpacity(0.0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(350), node);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    protected void playErrorShake(Node node) {
        if (node == null) return;
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
        shake.setFromX(0);
        shake.setByX(8);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0));
        shake.play();
    }

    protected void triggerShakeAnimation(VBox card) {
        playErrorShake(card);
    }

    protected void playSlideFadeIn(Node node) {
        if (node == null) return;
        node.setOpacity(0.0);
        node.setTranslateY(15);

        FadeTransition fade = new FadeTransition(Duration.millis(300), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(300), node);
        slide.setFromY(15);
        slide.setToY(0);

        ParallelTransition transition = new ParallelTransition(node, fade, slide);
        transition.play();
    }

    /**
     * Reusable method to spawn a modern, rounded success dialog.
     */
    protected void showSuccessDialog(String title, String message) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/success_dialog.fxml"));
            Parent root = loader.load();

            SuccessDialogController controller = loader.getController();
            controller.setDialogDetails(title, message);

            Stage dialogStage = new Stage();
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            javafx.stage.Window ownerWindow = javafx.stage.Window.getWindows().stream()
                    .filter(javafx.stage.Window::isShowing)
                    .findFirst()
                    .orElse(null);

            if (ownerWindow != null) {
                dialogStage.initOwner(ownerWindow);
            }

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            dialogStage.setScene(scene);

            dialogStage.setOnShown(e -> {
                if (ownerWindow != null) {
                    dialogStage.setX(ownerWindow.getX() + (ownerWindow.getWidth() - dialogStage.getWidth()) / 2);
                    dialogStage.setY(ownerWindow.getY() + (ownerWindow.getHeight() - dialogStage.getHeight()) / 2);
                }
            });

            dialogStage.showAndWait();
        } catch (IOException e) {
            System.err.println("Error loading success dialog: " + e.getMessage());
        }
    }

    /**
     * Reusable error dialog fallback method for all child controllers.
     */
    protected void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        javafx.stage.Window ownerWindow = javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .findFirst()
                .orElse(null);

        if (ownerWindow != null) {
            alert.initOwner(ownerWindow);
        }

        alert.showAndWait();
    }
}