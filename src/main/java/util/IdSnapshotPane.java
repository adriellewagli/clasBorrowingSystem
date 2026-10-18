package util;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * ID snapshot box (camera preview + Start/Snap/Retake) for pop-up forms such as the reservation dialog.
 * Works like the capture on the borrow request screen: the first click starts the camera, the second
 * click freezes the frame, "Retake" starts over. Call {@link #close()} when the dialog closes.
 */
public final class IdSnapshotPane extends VBox {

    private final ImageView preview = new ImageView();
    private final Label status = new Label("\uD83D\uDCF7 Click capture to start camera preview");
    private final Button btnCapture = new Button("\uD83D\uDCF7 Start Camera / Capture");
    private final Button btnRetake = new Button("Retake");

    private Webcam webcam;
    private volatile boolean cameraRunning = false;
    private volatile BufferedImage capturedFrame;
    private boolean captured = false;

    public IdSnapshotPane() {
        super(8);

        preview.setFitWidth(320);
        preview.setFitHeight(240);
        preview.setPreserveRatio(true);

        StackPane box = new StackPane(preview, status);
        box.setPrefSize(320, 240);
        box.setMinSize(320, 240);
        box.setMaxSize(320, 240);
        box.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8;");
        status.setStyle("-fx-text-fill: #94a3b8;");

        btnCapture.getStyleClass().add("btn-secondary");
        btnRetake.getStyleClass().add("btn-secondary");
        btnRetake.setVisible(false);
        btnRetake.setManaged(false);
        btnCapture.setOnAction(e -> onCapture());
        btnRetake.setOnAction(e -> onRetake());

        setAlignment(Pos.CENTER);
        getChildren().addAll(box, btnCapture, btnRetake);
    }

    public boolean isCaptured() { return captured && capturedFrame != null; }

    private void onCapture() {
        if (!cameraRunning) {
            try {
                webcam = Webcam.getDefault();
                if (webcam == null) { status.setText("No webcam hardware detected."); return; }
                webcam.setViewSize(WebcamResolution.VGA.getSize());
                webcam.open();
            } catch (Exception ex) {
                status.setText("Could not open the camera.");
                return;
            }
            cameraRunning = true;
            Thread t = new Thread(() -> {
                while (cameraRunning) {
                    BufferedImage image = webcam.getImage();
                    if (image != null) {
                        capturedFrame = image;
                        Image fx = SwingFXUtils.toFXImage(image, null);
                        Platform.runLater(() -> preview.setImage(fx));
                    }
                    try { Thread.sleep(40); } catch (InterruptedException ignored) {}
                }
            }, "id-snapshot-preview");
            t.setDaemon(true);
            t.start();
            status.setVisible(false);
            btnCapture.setText("\uD83D\uDCF8 Snap Photo");
        } else {
            cameraRunning = false;
            captured = true;
            close();
            status.setText("\u2713 Snapshot Captured");
            status.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            status.setVisible(true);
            btnCapture.setVisible(false);
            btnCapture.setManaged(false);
            btnRetake.setVisible(true);
            btnRetake.setManaged(true);
        }
    }

    private void onRetake() {
        captured = false;
        capturedFrame = null;
        close();
        preview.setImage(null);
        status.setText("\uD83D\uDCF7 Click capture to start camera preview");
        status.setStyle("-fx-text-fill: #94a3b8;");
        status.setVisible(true);
        btnCapture.setText("\uD83D\uDCF7 Start Camera / Capture");
        btnCapture.setVisible(true);
        btnCapture.setManaged(true);
        btnRetake.setVisible(false);
        btnRetake.setManaged(false);
    }

    /** Releases the camera. Safe to call more than once. */
    public void close() {
        cameraRunning = false;
        try {
            if (webcam != null && webcam.isOpen()) webcam.close();
        } catch (Exception ignored) {}
    }

    /** The captured photo as a 320x240 JPEG, or null if nothing was captured. */
    public byte[] getBytes() {
        if (!isCaptured()) return null;
        try {
            BufferedImage resized = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(capturedFrame, 0, 0, 320, 240, null);
            g.dispose();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(resized, "jpg", baos);
                return baos.toByteArray();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
