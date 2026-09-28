package model;

import java.time.LocalDate;

public class BorrowedItem {
    private int borrowId;
    private String equipmentName;
    private String category;
    private String serialNumber;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    private String status;

    public BorrowedItem(int borrowId, String equipmentName, String category, String serialNumber,
                        LocalDate borrowDate, LocalDate dueDate, String status) {
        this.borrowId = borrowId;
        this.equipmentName = equipmentName;
        this.category = category;
        this.serialNumber = serialNumber;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.status = status;
    }

    public int getBorrowId() { return borrowId; }
    public String getEquipmentName() { return equipmentName; }
    public String getCategory() { return category; }
    public String getSerialNumber() { return serialNumber; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }
}