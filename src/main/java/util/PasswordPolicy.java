package util;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * One place for the password rules, used by every screen that sets a password:
 *  - at least 8 characters
 *  - at least 1 uppercase letter (A-Z)
 *  - at least 1 lowercase letter (a-z)
 *  - at least 1 number (0-9)
 *  - 1 special character (! @ # $ % ...) is recommended; flip REQUIRE_SPECIAL to make it mandatory
 *
 * Only NEW passwords are checked. The login screen never applies these rules, so accounts that
 * already exist can still sign in until their owner changes the password.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final boolean REQUIRE_SPECIAL = false;

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9\\s]");

    private PasswordPolicy() {}

    public record Result(boolean length, boolean upper, boolean lower, boolean digit, boolean special) {
        /** True when every required rule is met. */
        public boolean ok() {
            return length && upper && lower && digit && (special || !REQUIRE_SPECIAL);
        }
    }

    public static Result check(String pw) {
        String p = pw == null ? "" : pw;
        return new Result(
                p.length() >= MIN_LENGTH,
                UPPER.matcher(p).find(),
                LOWER.matcher(p).find(),
                DIGIT.matcher(p).find(),
                SPECIAL.matcher(p).find());
    }

    /** @return null when the password is acceptable, otherwise a message listing what is missing. */
    public static String validate(String pw) {
        if (pw == null || pw.isEmpty()) return "Enter a password.";
        if (!pw.equals(pw.trim())) return "The password can't start or end with a space.";

        Result r = check(pw);
        if (r.ok()) return null;

        List<String> missing = new ArrayList<>();
        if (!r.length()) missing.add("at least " + MIN_LENGTH + " characters");
        if (!r.upper()) missing.add("an uppercase letter (A-Z)");
        if (!r.lower()) missing.add("a lowercase letter (a-z)");
        if (!r.digit()) missing.add("a number (0-9)");
        if (REQUIRE_SPECIAL && !r.special()) missing.add("a special character (! @ # $ %)");
        return "Password needs " + String.join(", ", missing) + ".";
    }

    /** A live checklist that turns each line green as the typed password meets it. */
    public static VBox checklist(ObservableValue<String> password) {
        Label[] rows = {
                new Label(), new Label(), new Label(), new Label(), new Label()
        };
        String[] text = {
                "At least " + MIN_LENGTH + " characters",
                "At least 1 uppercase letter (A-Z)",
                "At least 1 lowercase letter (a-z)",
                "At least 1 number (0-9)",
                "1 special character (! @ # $ %)" + (REQUIRE_SPECIAL ? "" : " - recommended")
        };

        Runnable refresh = () -> {
            Result r = check(password.getValue());
            boolean[] met = {r.length(), r.upper(), r.lower(), r.digit(), r.special()};
            for (int i = 0; i < rows.length; i++) {
                rows[i].setText((met[i] ? "\u2713  " : "\u25CB  ") + text[i]);
                rows[i].setStyle("-fx-font-size: 11.5px; -fx-font-weight: 600; -fx-text-fill: "
                        + (met[i] ? "#16a34a" : "#94a3b8") + ";");
            }
        };
        password.addListener((obs, o, n) -> refresh.run());
        refresh.run();

        VBox box = new VBox(2, rows);
        box.setStyle("-fx-padding: 2 0 2 0;");
        return box;
    }
}
