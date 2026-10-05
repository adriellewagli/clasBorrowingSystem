package util;

import dao.UserDAO;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.UserSession;

import java.util.regex.Pattern;

/**
 * "Change Login Details" pop-up for the signed-in account (any role).
 * Also shown right after login while the account still has the default username/password,
 * where "Later" lets the person keep the defaults.
 */
public final class CredentialsDialog {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{4,30}");

    private CredentialsDialog() {}

    /** @return true if the username and/or password was changed. */
    public static boolean show(Window owner, boolean firstLoginPrompt) {
        UserSession session = UserSession.getInstance();
        if (session == null || session.getUserId() <= 0) return false;

        UserDAO dao = new UserDAO();
        boolean[] changed = {false};

        Label title = new Label("Change Login Details");
        title.getStyleClass().add("modal-title");

        Label subtitle = new Label(firstLoginPrompt
                ? "Your account was set up with a default username and password. You can change them now, or keep them and change them later."
                : "Update your username and/or password. Your current password is needed to confirm.");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMinHeight(Region.USE_PREF_SIZE);

        TextField txtUser = DialogKit.textField("New username");
        txtUser.setText(session.getUsername());
        PasswordField txtCurrent = DialogKit.passwordField("Current password");
        PasswordField txtNew = DialogKit.passwordField("Leave blank to keep your current password");
        PasswordField txtConfirm = DialogKit.passwordField("Re-enter the new password");

        Label message = DialogKit.message();

        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-primary");
        save.setDefaultButton(true);
        Button cancel = new Button(firstLoginPrompt ? "Later" : "Cancel");
        cancel.getStyleClass().add("btn-secondary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, cancel, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = DialogKit.form(440);
        root.getChildren().addAll(
                title, subtitle,
                DialogKit.field("Username", txtUser),
                DialogKit.field("Current password", txtCurrent),
                DialogKit.field("New password (optional)", txtNew),
                DialogKit.field("Confirm new password", txtConfirm),
                message, buttons);

        Stage stage = DialogKit.modal(owner, "Change Login Details", root);
        cancel.setOnAction(e -> stage.close());

        save.setOnAction(e -> {
            String newUser = txtUser.getText() == null ? "" : txtUser.getText().trim();
            String current = txtCurrent.getText() == null ? "" : txtCurrent.getText().trim();
            String newPw = txtNew.getText() == null ? "" : txtNew.getText();
            String confirm = txtConfirm.getText() == null ? "" : txtConfirm.getText();

            if (!USERNAME.matcher(newUser).matches()) {
                DialogKit.showMessage(message, "Username must be 4-30 characters: letters, numbers, dot, dash or underscore.", false);
                return;
            }
            if (current.isEmpty()) {
                DialogKit.showMessage(message, "Enter your current password to confirm the change.", false);
                return;
            }

            boolean changePassword = !newPw.isEmpty();
            if (changePassword) {
                if (!newPw.equals(newPw.trim())) {
                    DialogKit.showMessage(message, "The new password can't start or end with a space.", false);
                    return;
                }
                if (newPw.length() < 6) {
                    DialogKit.showMessage(message, "The new password must be at least 6 characters.", false);
                    return;
                }
                if (!newPw.equals(confirm)) {
                    DialogKit.showMessage(message, "The new passwords don't match.", false);
                    return;
                }
                if (newPw.equals(current)) {
                    DialogKit.showMessage(message, "The new password must be different from the current one.", false);
                    return;
                }
            }

            boolean changeUsername = !newUser.equals(session.getUsername());
            if (!changePassword && !changeUsername) {
                DialogKit.showMessage(message, "Nothing to change yet - enter a new username or a new password.", false);
                return;
            }

            String error = dao.changeCredentials(session.getUserId(), newUser, current, changePassword ? newPw : null);
            if (error != null) {
                DialogKit.showMessage(message, error, false);
                return;
            }

            session.setUsername(newUser);
            changed[0] = true;
            stage.close();
        });

        stage.showAndWait();
        return changed[0];
    }
}
