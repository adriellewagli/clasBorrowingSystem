package model;

public class Equipment {

    private int id;
    private String name;
    private String category;
    private String status;
    private String serialNumber;
    private int quantity;

    public Equipment() {}

    // Main Constructor
    public Equipment(int id, String name, String category, String status, String serialNumber, int quantity) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.status = status;
        this.serialNumber = serialNumber;
        this.quantity = quantity;


    }

    // Constructor used by AdminInventoryController to add new items
    public Equipment(String name, String category, String serialNumber, String status) {
        this.name = name;
        this.category = category;
        this.serialNumber = serialNumber;
        this.status = status;
        this.quantity = 1; // Default quantity for new catalog items
    }
    // Overload for String ID (parses safely to int)
    public Equipment(String id, String name, String category, String status, String serialNumber, int quantity) {
        this(parseIdSafely(id), name, category, status, serialNumber, quantity);
    }

    // Overload without serialNumber
    public Equipment(int id, String name, String category, String status, int quantity) {
        this(id, name, category, status, "", quantity);
    }

    public int getId() {
        return id;
    }

    public String getIdAsString() {
        return String.valueOf(id);
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setId(String id) {
        this.id = parseIdSafely(id);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSerialNumber() {
        return serialNumber != null ? serialNumber : "";
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    private static int parseIdSafely(String val) {
        if (val == null) return 0;
        try {
            return Integer.parseInt(val.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}