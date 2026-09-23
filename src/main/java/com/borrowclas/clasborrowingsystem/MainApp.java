package com.borrowclas.clasborrowingsystem;

import controller.MainLayoutController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
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
        MainLayoutController.setView("/com/borrowclas/clasborrowingsystem/fxml/login.fxml");
    }

    public static void main(String[] args) {
        launch(args);
    }
}