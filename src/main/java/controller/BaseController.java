package controller;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;

public abstract class BaseController {

    /**
     * Primary single-scene navigation method.
     */
    protected <T> T navigateTo(String fxmlPath) throws IOException {
        return MainLayoutController.setView(fxmlPath);
    }

    /**
     * Overload for backward compatibility with existing controller calls.
     */
    protected <T> T navigateTo(ActionEvent event, String fxmlPath, String title) throws IOException {
        return navigateTo(fxmlPath);
    }

    // Plays a soft fade-in transition when a view/card appears
    protected void playFadeIn(Node node) {
        if (node == null) return;
        node.setOpacity(0.0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(350), node);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();
    }

    // Plays a fast horizontal shake transition for errors/failed validation
    protected void playErrorShake(Node node) {
        if (node == null) return;
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
        shake.setFromX(0);
        shake.setByX(8);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0)); // Reset position
        shake.play();
    }

    /**
     * Alias for playErrorShake to support existing controller calls.
     */
    protected void triggerShakeAnimation(VBox card) {
        playErrorShake(card);
    }

    // Combines fade-in and subtle slide-up when opening popups or dialogs
    protected void playSlideFadeIn(Node node) {
        if (node == null) return;
        node.setOpacity(0.0);
        node.setTranslateY(15);

        FadeTransition fade = new FadeTransition(Duration.millis(300), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(300), node);
        slide.setFromY(15);
        slide.setToY(0);

        ParallelTransition transition = new ParallelTransition(node, fade, slide);
        transition.play();
    }
}