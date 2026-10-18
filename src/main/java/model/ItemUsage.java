package model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** One row of the "Most Used Items" ranking. */
public class ItemUsage {
    private int rank;
    private final int equipmentId;
    private final String equipmentName;
    private final String category;
    private final String serialNumber;
    private final int timesBorrowed;
    private final LocalDate lastBorrowed;
    private final String currentStatus;

    public ItemUsage(int equipmentId, String equipmentName, String category, String serialNumber,
                     int timesBorrowed, LocalDate lastBorrowed, String currentStatus) {
        this.equipmentId = equipmentId;
        this.equipmentName = equipmentName;
        this.category = category;
        this.serialNumber = serialNumber;
        this.timesBorrowed = timesBorrowed;
        this.lastBorrowed = lastBorrowed;
        this.currentStatus = currentStatus;
    }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public int getEquipmentId() { return equipmentId; }
    public String getEquipmentName() { return equipmentName; }
    public String getCategory() { return category == null ? "" : category; }
    public String getSerialNumber() { return serialNumber == null ? "" : serialNumber; }
    public int getTimesBorrowed() { return timesBorrowed; }
    public LocalDate getLastBorrowed() { return lastBorrowed; }
    public String getCurrentStatus() { return currentStatus; }

    public String getLastBorrowedText() {
        return lastBorrowed == null ? "Never" : lastBorrowed.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }
}
