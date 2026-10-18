package model;

import java.time.LocalDate;

/** The narrative an admin wrote when a returned item came back damaged. */
public record DamageReport(int transactionId, String borrowerName, String condition,
                           String narrative, LocalDate reportedOn) {
}
