package model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Everything printed on a digital receipt for one borrow transaction. */
public record Receipt(
        int transactionId,
        String status,
        String itemName,
        String category,
        String serialNumber,
        String borrowerName,
        String borrowerType,
        String borrowerIdNumber,
        String programOrDept,
        LocalDate dateBorrowed,
        LocalDate dueDate,
        LocalDate returnedDate,      // null until the item is actually returned
        double borrowingFee,
        double latePenaltyPerDay,
        String initialCondition,
        String requestedBy,
        String processedBy) {

    public String receiptNumber() {
        return String.format("CBS-%06d", transactionId);
    }

    /** Days past the due date (up to the return date, or today if still out). */
    public long lateDays() {
        if (dueDate == null) return 0;
        LocalDate end = returnedDate != null ? returnedDate : LocalDate.now();
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, end));
    }

    public double lateFee() {
        return lateDays() * latePenaltyPerDay;
    }

    public double totalDue() {
        return borrowingFee + lateFee();
    }
}
