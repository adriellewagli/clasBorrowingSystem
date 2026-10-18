package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

public class BorrowedItem {
    private int borrowId;
    private int equipmentId;
    private String equipmentName;
    private String category;
    private String borrowerName;
    private String borrowerType;
    private String borrowerIdNumber;
    private String requestedBy; // Stores the username of who submitted the request
    private String serialNumber;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    private String status;
    private String processedBy; // Stores the username of who approved it

    // --- billing ---
    private String requestType = "Borrow";   // "Borrow" or "Reservation"
    private double fee;                      // this item's borrowing fee (set by the Super Admin)
    private boolean feePending;              // no longer used: the fee is known from the start (always false)
    private double latePenaltyPerDay;        // this item's late fee per day (set by the Super Admin)
    private long lateDays;                   // days past the due date
    private double latePenalty;              // lateDays x penalty per day
    private LocalDateTime approvedAt;

    public BorrowedItem() {}

    public BorrowedItem(int borrowId, int equipmentId, String equipmentName, String category,
                        String serialNumber, LocalDate borrowDate, LocalDate dueDate, String status) {
        this.borrowId = borrowId;
        this.equipmentId = equipmentId;
        this.equipmentName = equipmentName;
        this.category = category;
        this.serialNumber = serialNumber;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.status = status;
    }

    public BorrowedItem(int borrowId, String equipmentName, String category, String serialNumber,
                        LocalDate borrowDate, LocalDate dueDate, String status) {
        this(borrowId, 0, equipmentName, category, serialNumber, borrowDate, dueDate, status);
    }

    // Getters & Setters
    public int getBorrowId() { return borrowId; }
    public void setBorrowId(int borrowId) { this.borrowId = borrowId; }

    public int getEquipmentId() { return equipmentId; }
    public void setEquipmentId(int equipmentId) { this.equipmentId = equipmentId; }

    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBorrowerName() {
        return (borrowerName != null && !borrowerName.isBlank()) ? borrowerName : "N/A";
    }
    public void setBorrowerName(String borrowerName) { this.borrowerName = borrowerName; }

    public String getBorrowerType() { return borrowerType == null ? "" : borrowerType; }
    public void setBorrowerType(String borrowerType) { this.borrowerType = borrowerType; }

    public String getBorrowerIdNumber() { return borrowerIdNumber == null ? "" : borrowerIdNumber; }
    public void setBorrowerIdNumber(String borrowerIdNumber) { this.borrowerIdNumber = borrowerIdNumber; }

    public String getRequestedBy() {
        return (requestedBy != null && !requestedBy.isBlank()) ? requestedBy : "N/A";
    }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public LocalDate getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDate borrowDate) { this.borrowDate = borrowDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProcessedBy() {
        return (processedBy != null && !processedBy.isBlank()) ? processedBy : "Pending";
    }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }

    // ------------------------------------------------------------ billing

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public double getFee() { return fee; }
    public void setFee(double fee) { this.fee = fee; }

    public boolean isFeePending() { return feePending; }
    public void setFeePending(boolean feePending) { this.feePending = feePending; }

    public double getLatePenaltyPerDay() { return latePenaltyPerDay; }
    public void setLatePenaltyPerDay(double latePenaltyPerDay) { this.latePenaltyPerDay = latePenaltyPerDay; }

    public long getLateDays() { return lateDays; }
    public void setLateDays(long lateDays) { this.lateDays = lateDays; }

    public double getLatePenalty() { return latePenalty; }
    public void setLatePenalty(double latePenalty) { this.latePenalty = latePenalty; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    /** Text for the FEE column: the item's borrowing fee (the Super Admin sets it per item). */
    public String getFeeText() {
        return feePending ? "To be set" : money(fee);
    }

    /** Text for the PENALTY column: blank dash when on time. */
    public String getPenaltyText() {
        if (latePenalty <= 0) return "\u2014";
        return money(latePenalty) + " (" + lateDays + (lateDays == 1 ? " day)" : " days)");
    }

    /** One-line note for the user's Request History "Remarks" column. */
    public String getRemarks() {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        switch (s) {
            case "pending":   return "Waiting for admin approval";
            case "reserved":  return "Reserved for pick-up on " + (borrowDate == null ? "-" : borrowDate);
            case "rejected":  return "Request was rejected";
            case "cancelled": return "Cancelled by you";
            default: break;
        }
        StringBuilder sb = new StringBuilder("Fee " + money(fee));
        if (latePenalty > 0) {
            sb.append(" + late penalty ").append(money(latePenalty))
              .append(" (").append(lateDays).append(lateDays == 1 ? " day" : " days").append(" late)");
        }
        return sb.toString();
    }

    private static String money(double v) {
        return String.format("\u20B1%,.2f", v);
    }
}
