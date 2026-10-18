package util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.regex.Pattern;

/**
 * Rules for a borrower's full name:
 *  - letters only (including accented letters like ñ, é), plus spaces, hyphens, apostrophes and periods
 *    (so real names such as "Ma. Cristina", "Dela-Cruz", "O'Brien" and "Jr." still work)
 *  - no digits or other symbols
 *  - maximum MAX_LENGTH characters
 */
public final class NameValidator {

    public static final int MAX_LENGTH = 50;

    // Characters allowed while typing/pasting.
    private static final Pattern ALLOWED_CHARS = Pattern.compile("[\\p{L} .'\\-]*");
    // A complete, valid name: starts with a letter, then only allowed characters.
    private static final Pattern VALID_NAME = Pattern.compile("^\\p{L}[\\p{L} .'\\-]*$");

    private NameValidator() {}

    /** Blocks invalid characters and extra length as the user types or pastes. */
    public static void restrict(TextField field) {
        if (field == null) return;
        field.setTextFormatter(new TextFormatter<String>(change -> {
            String newText = change.getControlNewText();
            if (newText.length() > MAX_LENGTH) return null;
            if (!ALLOWED_CHARS.matcher(newText).matches()) return null;
            return change;
        }));
    }

    /** Returns an error message to show the user, or null if the name is valid. */
    public static String validate(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "Please enter the full borrower name.";
        if (n.length() > MAX_LENGTH) return "Name must be " + MAX_LENGTH + " characters or less.";
        if (!VALID_NAME.matcher(n).matches())
            return "Name can only contain letters, spaces, hyphens, apostrophes and periods.";
        long letters = n.chars().filter(Character::isLetter).count();
        if (letters < 2) return "Please enter the full name (at least 2 letters).";
        return null;
    }
}