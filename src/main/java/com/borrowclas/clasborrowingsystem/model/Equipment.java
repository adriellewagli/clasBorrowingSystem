package model;

public class Equipment {
    private int id;
    private String name;
    private String category;
    private String status; // "AVAILABLE", "BORROWED", "REPAIR"
    private String serialNumber;

    public Equipment(int id, String name, String category, String status, String serialNumber) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.status = status;
        this.serialNumber = serialNumber;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getStatus() { return status; }
    public String getSerialNumber() { return serialNumber; }
}