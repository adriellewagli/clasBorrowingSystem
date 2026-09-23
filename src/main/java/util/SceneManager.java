package com.borrowclas.clasborrowingsystem.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class SceneManager {

    /**
     * Switch scenes seamlessly while preserving full-screen state without hint popups.
     */
    public static <T> T switchScene(Stage stage, String fxmlPath, String title) throws IOException {
        boolean wasFullScreen = stage.isFullScreen();

        FXMLLoader loader = new FXMLLoader(SceneManager.class.getResource(fxmlPath));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle(title);

        // Suppress hint popup on scene transitions
        stage.setFullScreenExitHint("");

        if (wasFullScreen) {
            stage.setFullScreen(true);
        }

        return loader.getController();
    }
}