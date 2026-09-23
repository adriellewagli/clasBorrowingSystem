package ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Row model for the "Available Equipment Overview" table.
 * Property-based so it can be used directly with PropertyValueFactory.
 */
public class Equipment {
    private final IntegerProperty equipmentId;
    private final StringProperty itemName;
    private final StringProperty category;
    private final StringProperty serialNumber;
    private final StringProperty status;

    public Equipment(int equipmentId, String itemName, String category, String serialNumber, String status) {
        this.equipmentId = new SimpleIntegerProperty(equipmentId);
        this.itemName = new SimpleStringProperty(itemName);
        this.category = new SimpleStringProperty(category);
        this.serialNumber = new SimpleStringProperty(serialNumber);
        this.status = new SimpleStringProperty(status);
    }

    public int getEquipmentId() { return equipmentId.get(); }
    public IntegerProperty equipmentIdProperty() { return equipmentId; }

    public String getItemName() { return itemName.get(); }
    public StringProperty itemNameProperty() { return itemName; }

    public String getCategory() { return category.get(); }
    public StringProperty categoryProperty() { return category; }

    public String getSerialNumber() { return serialNumber.get(); }
    public StringProperty serialNumberProperty() { return serialNumber; }

    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }
}
