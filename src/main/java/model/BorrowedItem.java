package model;

import java.time.LocalDate;

public class BorrowedItem {
    private int borrowId;
    private int equipmentId;
    private String equipmentName;
    private String category;
    private String borrowerName;
    private String requestedBy; // Stores the username of who submitted the request
    private String serialNumber;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    private String status;
    private String processedBy; // Stores the username of who approved it

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
}