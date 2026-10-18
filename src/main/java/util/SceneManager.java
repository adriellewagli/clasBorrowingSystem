package util;

import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneManager {

    private static Stage primaryStage;

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    /**
     * Switch scene with an automatic smooth loading transition screen.
     * Works across any form transition in the application.
     */
    public static void switchSceneWithLoading(String fxmlPath) {
        if (primaryStage == null) {
            System.err.println("[SceneManager Error] Primary stage is not initialized!");
            return;
        }

        try {
            // 1. Immediately display the Loading Overlay
            FXMLLoader loadingLoader = new FXMLLoader(SceneManager.class.getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/loading_overlay.fxml"));
            Parent loadingRoot = loadingLoader.load();

            if (primaryStage.getScene() == null) {
                primaryStage.setScene(new Scene(loadingRoot));
            } else {
                primaryStage.getScene().setRoot(loadingRoot);
            }

            // 2. Load the target FXML asynchronously in a background thread
            Task<Parent> loadTask = new Task<>() {
                @Override
                protected Parent call() throws Exception {
                    // Small artificial delay (350ms) to ensure smooth transition UX
                    Thread.sleep(350);

                    FXMLLoader loader = new FXMLLoader(SceneManager.class.getResource(fxmlPath));
                    return loader.load();
                }
            };

            // 3. On successful load, swap root to target view
            loadTask.setOnSucceeded(e -> {
                Parent targetRoot = loadTask.getValue();
                primaryStage.getScene().setRoot(targetRoot);
            });

            // 4. Handle unexpected load errors
            loadTask.setOnFailed(e -> {
                Throwable ex = loadTask.getException();
                System.err.println("[SceneManager Error] Failed to switch scene to: " + fxmlPath);
                ex.printStackTrace();
            });

            Thread thread = new Thread(loadTask);
            thread.setDaemon(true);
            thread.start();

        } catch (IOException e) {
            System.err.println("[SceneManager Error] Failed to load loading overlay.");
            e.printStackTrace();
        }
    }
}