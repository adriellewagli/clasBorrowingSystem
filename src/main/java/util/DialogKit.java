package util;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Small helpers for the pop-up forms (change login details, create account). */
public final class DialogKit {

    private static final String CSS = "/com/borrowclas/clasborrowingsystem/css/dashboard.css";

    private DialogKit() {}

    public static Stage modal(Window owner, String title, Parent root) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle(title);
        stage.setResizable(false);

        Scene scene = new Scene(root);
        java.net.URL css = DialogKit.class.getResource(CSS);
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        return stage;
    }

    public static VBox form(double width) {
        VBox box = new VBox(14);
        box.setPadding(new Insets(28));
        box.setPrefWidth(width);
        box.setStyle("-fx-background-color: #ffffff;");
        return box;
    }

    public static VBox field(String label, Node input) {
        Label l = new Label(label);
        l.getStyleClass().add("modal-label");
        return new VBox(6, l, input);
    }

    public static TextField textField(String prompt) {
        TextField t = new TextField();
        t.setPromptText(prompt);
        t.getStyleClass().add("clean-search-field");
        return t;
    }

    public static PasswordField passwordField(String prompt) {
        PasswordField p = new PasswordField();
        p.setPromptText(prompt);
        p.getStyleClass().add("clean-search-field");
        return p;
    }

    public static Label message() {
        Label l = new Label();
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    public static void showMessage(Label label, String text, boolean success) {
        label.setText(text);
        label.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: "
                + (success ? "#16a34a" : "#dc2626") + ";");
    }
}
