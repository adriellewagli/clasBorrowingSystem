package util;

import java.util.List;

/** The academic programs / departments offered by CLAS (shared by the borrow and reserve forms). */
public final class Programs {
    private Programs() {}

    public static final List<String> CLAS = List.of(
            "AB Political Science",
            "BA Communication",
            "Bachelor of Public Administration",
            "Bachelor of Science in Computer Science",
            "Bachelor of Science in Entertainment and Multimedia Computing",
            "Bachelor of Science in Information System",
            "Bachelor of Science in Information Technology",
            "Bachelor of Science in Mathematics",
            "Bachelor of Science in Psychology"
    );
}
