package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
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
     * Swaps the view inside the container without creating a new Scene or Stage.
     */
    public static <T> T setView(String fxmlPath) throws IOException {
        if (instance == null || instance.contentArea == null) {
            throw new IllegalStateException("MainLayoutController is not initialized.");
        }

        FXMLLoader loader = new FXMLLoader(MainLayoutController.class.getResource(fxmlPath));
        Node view = loader.load();

        // Smoothly replace content inside the fixed Scene
        instance.contentArea.getChildren().setAll(view);

        return loader.getController();
    }
}