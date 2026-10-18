package com.borrowclas.clasborrowingsystem;

import atlantafx.base.theme.PrimerLight;
import controller.MainLayoutController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import util.FontManager;                      // ← add


public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Set AtlantaFX Primer Light as the default base theme for all JavaFX controls
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // Load the single master shell container
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/main_layout.fxml"));
        Scene scene = new Scene(loader.load());

        FontManager.install(scene);           // ← add (once, here)


        // --- GLOBAL FULL-SCREEN SHORTCUT (F10 / F11 TOGGLE) ---
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F10 || event.getCode() == KeyCode.F11) {
                boolean isFullScreen = primaryStage.isFullScreen();
                primaryStage.setFullScreen(!isFullScreen);
                event.consume(); // Intercept event so child controls don't handle F10/F11
            }
        });

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