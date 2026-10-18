package util;

import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Small field validators that give instant visual feedback (red box + message). */
public final class InputRules {

    public static final Pattern STUDENT_NO = Pattern.compile("^\\d{8}-[A-Za-z0-9]$");
    public static final String STUDENT_NO_ERROR = "Invalid student number. Use the format 20250045-N";

    private static final String ERROR_CLASS = "input-error";

    private InputRules() {}

    public static boolean isValidStudentNumber(String s) {
        return s != null && STUDENT_NO.matcher(s.trim()).matches();
    }

    /** Turns the red "invalid" look on or off for any field. */
    public static void setInvalid(Control control, boolean invalid) {
        if (control == null) return;
        control.getStyleClass().remove(ERROR_CLASS);
        if (invalid) control.getStyleClass().add(ERROR_CLASS);
    }

    /**
     * Upper-cases what is typed and flags the field red when the student number is wrong:
     *  - straight away if it has illegal characters or is too long,
     *  - when the person leaves the field while it is incomplete,
     *  - cleared again the moment it becomes valid.
     * @param error optional label under the field that shows the message
     */
    public static void bindStudentNumber(TextField field, Label error) {
        bindStudentNumber(field, error, () -> true);
    }

    /** Same, but the check only runs while {@code enabled} is true (e.g. only for the Student option). */
    public static void bindStudentNumber(TextField field, Label error, BooleanSupplier enabled) {
        if (field == null) return;
        if (error != null) {
            error.getStyleClass().add("field-error-text");
            error.setWrapText(true);
            error.setManaged(false);
            error.setVisible(false);
        }

        Runnable show = () -> flag(field, error, true);
        Runnable clear = () -> flag(field, error, false);

        field.textProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || !enabled.getAsBoolean()) return;
            String up = newV.toUpperCase();
            if (!up.equals(newV)) { field.setText(up); return; }

            String t = newV.trim();
            if (t.isEmpty() || isValidStudentNumber(t)) clear.run();
            else if (t.length() > 10 || !t.matches("[0-9A-Z-]*")) show.run();
            else if (field.getStyleClass().contains(ERROR_CLASS)) show.run(); // stay red until fixed
        });

        field.focusedProperty().addListener((obs, was, now) -> {
            if (was && !now && enabled.getAsBoolean()) {
                String t = field.getText() == null ? "" : field.getText().trim();
                if (t.isEmpty() || isValidStudentNumber(t)) clear.run();
                else show.run();
            }
        });
    }

    /** Marks the field invalid with the standard message (used when Submit is pressed). */
    public static void flagStudentNumber(TextField field, Label error, boolean invalid) {
        flag(field, error, invalid);
    }

    private static void flag(TextField field, Label error, boolean invalid) {
        setInvalid(field, invalid);
        if (error != null) {
            error.setText(invalid ? STUDENT_NO_ERROR : "");
            error.setVisible(invalid);
            error.setManaged(invalid);
        }
    }
}
