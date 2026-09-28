package com.borrowclas.clasborrowingsystem;

import atlantafx.base.theme.PrimerLight;
import controller.MainLayoutController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Set AtlantaFX Primer Light as the default base theme for all JavaFX controls
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Load the single master shell container
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/main_layout.fxml"));
        Scene scene = new Scene(loader.load());

        primaryStage.setTitle("CLAS Borrowing System");
        primaryStage.setScene(scene);

        // Configure full screen ONCE for the entire application lifecycle
        primaryStage.setFullScreenExitHint("");
        primaryStage.setFullScreenExitKeyCombination(KeyCombination.valueOf("ESC"));
        primaryStage.setResizable(true);
        primaryStage.setFullScreen(true);

        primaryStage.show();

        // Load initial screen into the container
        MainLayoutController.setView("/com/borrowclas/clasborrowingsystem/fxml/auth/login.fxml");
    }

    public static void main(String[] args) {
        launch(args);
    }
}