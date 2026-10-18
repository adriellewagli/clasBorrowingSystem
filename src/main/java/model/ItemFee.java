package model;

/** One row of the Super Admin's "Fees & Penalties" table: an item and the fees that apply to it. */
public class ItemFee {
    private final int equipmentId;
    private final String equipmentName;
    private final String category;
    private final String serialNumber;
    private final double borrowFee;
    private final double lateFeePerDay;

    public ItemFee(int equipmentId, String equipmentName, String category, String serialNumber,
                   double borrowFee, double lateFeePerDay) {
        this.equipmentId = equipmentId;
        this.equipmentName = equipmentName;
        this.category = category;
        this.serialNumber = serialNumber;
        this.borrowFee = borrowFee;
        this.lateFeePerDay = lateFeePerDay;
    }

    public int getEquipmentId() { return equipmentId; }
    public String getEquipmentName() { return equipmentName == null ? "" : equipmentName; }
    public String getCategory() { return category == null ? "" : category; }
    public String getSerialNumber() { return serialNumber == null ? "" : serialNumber; }
    public double getBorrowFee() { return borrowFee; }
    public double getLateFeePerDay() { return lateFeePerDay; }

    /** Text for the BORROWING FEE column. */
    public String getBorrowFeeText() { return money(borrowFee); }

    /** Text for the LATE FEE column. */
    public String getLateFeeText() { return money(lateFeePerDay) + " / day"; }

    private static String money(double v) {
        return String.format("\u20B1%,.2f", v);
    }
}
