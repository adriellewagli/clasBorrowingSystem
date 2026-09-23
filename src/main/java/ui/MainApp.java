package ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    // Fixed application window size — used everywhere a Scene is created
    // (here, and in LoginController / DashboardController) so the layout
    // never has to reflow for a different window size.
    public static final double APP_WIDTH = 900;
    public static final double APP_HEIGHT = 600;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/login.fxml"));
        Scene scene = new Scene(loader.load(), APP_WIDTH, APP_HEIGHT);

        primaryStage.setTitle("CLAS Borrowing System");
        primaryStage.setScene(scene);

        // Fixed size window: no maximizing, no dragging to resize.
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
