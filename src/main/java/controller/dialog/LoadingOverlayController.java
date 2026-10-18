package controller.dialog;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class LoadingOverlayController {

    @FXML private StackPane overlayRoot;
    @FXML private Circle dot1;
    @FXML private Circle dot2;
    @FXML private Circle dot3;
    @FXML private Label lblLoadingText;

    @FXML
    public void initialize() {
        // Fade in the background smoothly
        overlayRoot.setOpacity(0.0);
        FadeTransition fade = new FadeTransition(Duration.millis(250), overlayRoot);
        fade.setToValue(1.0);
        fade.play();

        // Start all dots instantly, but at different phases of the animation (no waiting)
        animateDot(dot1, 0);
        animateDot(dot2, 150);
        animateDot(dot3, 300);
    }

    private void animateDot(Circle dot, int startOffsetMillis) {
        TranslateTransition bounce = new TranslateTransition(Duration.millis(400), dot);
        bounce.setByY(-12);
        bounce.setAutoReverse(true);
        bounce.setCycleCount(TranslateTransition.INDEFINITE);
        bounce.setInterpolator(Interpolator.EASE_BOTH);

        // playFrom jumps immediately into the animation cycle
        bounce.playFrom(Duration.millis(startOffsetMillis));
    }

    public void setLoadingText(String text) {
        lblLoadingText.setText(text);
    }

    public void fadeOut(Runnable onFinished) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), overlayRoot);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        fadeOut.play();
    }
}