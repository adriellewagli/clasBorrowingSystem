package controller;

import controller.dialog.LoadingOverlayController;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;

public class MainLayoutController {

    @FXML private StackPane contentArea;
    private static MainLayoutController instance;

    @FXML
    public void initialize() {
        instance = this;

        // Register shortcut once the content area is attached to a Scene
        contentArea.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                setupFullScreenShortcut(newScene);
            }
        });
    }

    public static <T> T setView(String fxmlPath) throws IOException {
        if (instance == null || instance.contentArea == null) {
            throw new IllegalStateException("MainLayoutController is not initialized.");
        }

        try {
            // 1. Load the TARGET view synchronously
            FXMLLoader targetLoader = new FXMLLoader(MainLayoutController.class.getResource(fxmlPath));
            Node targetView = targetLoader.load();
            T targetController = targetLoader.getController();

            // Ensure full-screen listener is active on the current scene
            if (instance.contentArea.getScene() != null) {
                setupFullScreenShortcut(instance.contentArea.getScene());
            }

            // 2. Load the LOADING OVERLAY
            FXMLLoader loadingLoader = new FXMLLoader(MainLayoutController.class.getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/loading_overlay.fxml"));
            Node loadingOverlay = loadingLoader.load();
            LoadingOverlayController loadingController = loadingLoader.getController();

            // 3. Stack both: Target view in the back, Loading Overlay in the front
            instance.contentArea.getChildren().setAll(targetView, loadingOverlay);

            // 4. Artificial delay to let the spinner play, then fade out smoothly
            PauseTransition delay = new PauseTransition(Duration.millis(300));
            delay.setOnFinished(e -> {
                loadingController.fadeOut(() -> {
                    instance.contentArea.getChildren().remove(loadingOverlay);
                });
            });
            delay.play();

            // 5. Safely return the controller
            return targetController;

        } catch (IOException e) {
            System.err.println("[MainLayoutController Error] Failed to switch view to: " + fxmlPath);
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Attaches a global EventFilter to listen for F10 / F11 key presses and toggle Full Screen mode.
     */
    public static void setupFullScreenShortcut(Scene scene) {
        if (scene == null) return;

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F10 || event.getCode() == KeyCode.F11) {
                Stage stage = (Stage) scene.getWindow();
                if (stage != null) {
                    boolean isFullScreen = stage.isFullScreen();
                    stage.setFullScreen(!isFullScreen);
                    stage.setFullScreenExitHint("Press F10 or ESC to exit Full Screen");
                }
                event.consume(); // Stops the key press from triggering other UI elements
            }
        });
    }
}