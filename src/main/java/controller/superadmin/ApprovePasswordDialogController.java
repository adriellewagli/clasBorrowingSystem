package controller.superadmin;

import controller.dialog.ConfirmDialogController;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

public class ApprovePasswordDialogController {

    @FXML private VBox cardContainer;
    @FXML private Label lblSubtitle;
    @FXML private TextField txtTempPassword;
    @FXML private Label lblError;

    private Stage dialogStage;
    private String resultPassword = null;
    private boolean confirmed = false;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void initData(String username, String defaultPassword) {
        if (lblSubtitle != null) {
            lblSubtitle.setText("Assigning temporary password for: " + username);
        }
        if (txtTempPassword != null) {
            txtTempPassword.setText(defaultPassword);
            Platform.runLater(() -> {
                txtTempPassword.requestFocus();
                txtTempPassword.selectAll();
            });
        }
    }

    @FXML
    private void handleConfirm(ActionEvent event) {
        String temporaryPassword = txtTempPassword.getText() == null ? "" : txtTempPassword.getText().trim();
        if (temporaryPassword.isEmpty()) {
            lblError.setText("Password cannot be empty.");
            lblError.setVisible(true);
            lblError.setManaged(true);
            return;
        }

        // Use your ConfirmDialogController reference for the final confirmation prompt
        Window owner = dialogStage;
        String message = String.format("The user will be assigned the temporary password:\n\n%s\n\nDo you want to proceed?", temporaryPassword);

        boolean confirmedAction = ConfirmDialogController.show(
                "Confirm Approval",
                message,
                owner
        );

        if (confirmedAction) {
            resultPassword = temporaryPassword;
            confirmed = true;
            closeWithFade();
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        confirmed = false;
        closeWithFade();
    }

    private void closeWithFade() {
        if (dialogStage == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(150), dialogStage.getScene().getRoot());
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> dialogStage.close());
        ft.play();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getResultPassword() {
        return resultPassword;
    }
}