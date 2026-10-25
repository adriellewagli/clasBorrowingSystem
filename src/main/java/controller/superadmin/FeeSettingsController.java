package controller.superadmin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import model.ItemFee;
import util.ItemFeeDialog;
import util.LiveRefresh;
import util.TableCells;

import java.util.Comparator;
import java.util.List;

/**
 * Super Admin tab "Fees & Penalties": the borrowing fee and the late fee per day of every item.
 * The admin who approves a request no longer types a fee - the item's fee set here is used.
 */
public class FeeSettingsController extends BaseController {

    @FXML private TextField txtSearch;
    @FXML private Button btnRefresh;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;
    @FXML private TableView<ItemFee> tblFees;
    @FXML private TableColumn<ItemFee, String> colName;
    @FXML private TableColumn<ItemFee, String> colCategory;
    @FXML private TableColumn<ItemFee, String> colSerial;
    @FXML private TableColumn<ItemFee, String> colBorrowFee;
    @FXML private TableColumn<ItemFee, String> colLateFee;
    @FXML private TableColumn<ItemFee, Void> colAction;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<ItemFee> fees = FXCollections.observableArrayList();
    private FilteredList<ItemFee> filtered;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colBorrowFee.setCellValueFactory(new PropertyValueFactory<>("borrowFeeText"));
        colLateFee.setCellValueFactory(new PropertyValueFactory<>("lateFeeText"));

        colName.setCellFactory(TableCells.primaryCell());
        colCategory.setCellFactory(TableCells.mutedCell());
        colSerial.setCellFactory(TableCells.monoCell());
        colBorrowFee.setCellFactory(TableCells.primaryCell());
        colLateFee.setCellFactory(TableCells.primaryCell());
        colAction.setCellFactory(TableCells.<ItemFee>actionButton("Edit fees", this::editFees));

        // the fee columns hold text such as "\u20B11,250.00", so sort them by the amount, not alphabetically
        colBorrowFee.setComparator(Comparator.comparingDouble(FeeSettingsController::amountOf));
        colLateFee.setComparator(Comparator.comparingDouble(FeeSettingsController::amountOf));

        tblFees.getColumns().forEach(col -> col.setReorderable(false));
        TableCells.modernize(tblFees);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);

        filtered = new FilteredList<>(fees, f -> true);
        SortedList<ItemFee> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblFees.comparatorProperty());
        util.TablePager.install(tblFees, filtered, sorted);
        TableCells.bindCount(lblRowCount, filtered, fees);

        txtSearch.textProperty().addListener((obs, o, n) -> filtered.setPredicate(f -> {
            if (n == null || n.isBlank()) return true;
            String q = n.toLowerCase().trim();
            return has(f.getEquipmentName(), q) || has(f.getCategory(), q) || has(f.getSerialNumber(), q);
        }));

        fees.setAll(fetchFees());
        // fees changed from another computer show up here by themselves
        LiveRefresh.poll(tblFees, fees, this::fetchFees, null);
    }

    private static boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    /** "\u20B11,250.00" or "\u20B150.00 / day" -> 1250.0 / 50.0 */
    private static double amountOf(String text) {
        if (text == null) return 0;
        try {
            return Double.parseDouble(text.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Database read only - safe to run off the UI thread. */
    private List<ItemFee> fetchFees() {
        return equipmentDAO.getItemFees();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        LiveRefresh.manualRefresh(btnRefresh, tblFees, fees, this::fetchFees, null);
    }

    private void editFees(ItemFee item) {
        if (item == null) return;
        Window owner = tblFees.getScene() != null ? tblFees.getScene().getWindow() : null;

        double[] result = ItemFeeDialog.show(owner, item);
        if (result == null) return;

        String error = equipmentDAO.updateItemFees(item.getEquipmentId(), result[0], result[1]);
        if (error != null) {
            showErrorDialog("Could not save the fees", error);
            return;
        }

        // any background read that started before this save is now stale - drop it, then re-read
        LiveRefresh.markChanged();
        LiveRefresh.background(this::fetchFees, rows -> {
            if (rows != null) {
                fees.setAll(rows);
                tblFees.refresh();
            }
        });
        showSuccessDialog("Fees Saved", item.getEquipmentName() + String.format(
                " now costs \u20B1%,.2f to borrow, with a late fee of \u20B1%,.2f per day. "
                        + "This applies to requests approved from now on.", result[0], result[1]));
    }
}
