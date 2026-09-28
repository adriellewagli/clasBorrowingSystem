package controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainLayoutController {

    @FXML private StackPane contentArea;

    private static MainLayoutController instance;

    @FXML
    public void initialize() {
        instance = this;
    }

    /**
     * Swaps the view inside the container without creating a new Scene or Stage,
     * featuring an integrated loading screen overlay transition.
     */
    public static <T> T setView(String fxmlPath) throws IOException {
        if (instance == null || instance.contentArea == null) {
            throw new IllegalStateException("MainLayoutController is not initialized.");
        }

        try {
            // 1. Instantly display loading screen inside contentArea
            FXMLLoader loadingLoader = new FXMLLoader(MainLayoutController.class.getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/loading_overlay.fxml"));
            Parent loadingRoot = loadingLoader.load();
            instance.contentArea.getChildren().setAll(loadingRoot);

            final Object[] controllerContainer = new Object[1];
            final Node[] viewContainer = new Node[1];

            // 2. Load target view asynchronously in a background thread
            Task<Void> loadTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    // Small artificial delay (300ms) for smooth visual transition
                    Thread.sleep(300);

                    FXMLLoader loader = new FXMLLoader(MainLayoutController.class.getResource(fxmlPath));
                    viewContainer[0] = loader.load();
                    controllerContainer[0] = loader.getController();
                    return null;
                }
            };

            // 3. Swap loading view for loaded view upon completion
            loadTask.setOnSucceeded(e -> {
                if (viewContainer[0] != null) {
                    instance.contentArea.getChildren().setAll(viewContainer[0]);
                }
            });

            loadTask.setOnFailed(e -> {
                Throwable ex = loadTask.getException();
                System.err.println("[MainLayoutController Error] Failed to switch view to: " + fxmlPath);
                ex.printStackTrace();
            });

            Thread thread = new Thread(loadTask);
            thread.setDaemon(true);
            thread.start();

            @SuppressWarnings("unchecked")
            T controller = (T) controllerContainer[0];
            return controller;

        } catch (IOException e) {
            System.err.println("[MainLayoutController Error] Could not load loading_overlay.fxml");
            e.printStackTrace();
            throw e;
        }
    }
}