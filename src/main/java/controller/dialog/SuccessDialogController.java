package controller.dialog;

        import javafx.fxml.FXML;
        import javafx.scene.control.Label;
        import javafx.stage.Stage;

        public class SuccessDialogController {

        @FXML private Label lblTitle;
        @FXML private Label lblMessage;

        public void setDialogDetails(String title, String message) {
        lblTitle.setText(title);
        lblMessage.setText(message);
        }

        @FXML
        private void handleClose() {
        Stage stage = (Stage) lblTitle.getScene().getWindow();
        stage.close();
        }
        }