package controller.user;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.Equipment;
import model.UserSession;
import util.ActionFilter;
import util.LiveRefresh;
import util.TableCells;

import java.util.List;

/** Read-only equipment catalog, shared by Staff, Admin and Super Admin. No action buttons. */
public class EquipmentCatalogController extends BaseController {

    @FXML private Button btnRefresh;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;
    @FXML private TableView<Equipment> tblCatalog;
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colStatus;
    @FXML private TableColumn<Equipment, Double> colBorrowFee;
    @FXML private TableColumn<Equipment, Double> colLateFee;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<Equipment> catalog = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colBorrowFee.setCellValueFactory(new PropertyValueFactory<>("borrowFee"));
        colLateFee.setCellValueFactory(new PropertyValueFactory<>("lateFeePerDay"));

        colName.setCellFactory(TableCells.primaryCell());
        colCategory.setCellFactory(TableCells.mutedCell());
        colSerial.setCellFactory(TableCells.monoCell());
        colStatus.setCellFactory(TableCells.statusPill());
        colBorrowFee.setCellFactory(col -> moneyCell());
        colLateFee.setCellFactory(col -> moneyCell());

        tblCatalog.getColumns().forEach(c -> c.setReorderable(false));
        TableCells.modernize(tblCatalog);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);

        FilteredList<Equipment> filtered = new FilteredList<>(catalog, e -> true);
        SortedList<Equipment> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblCatalog.comparatorProperty());
        util.TablePager.install(tblCatalog, filtered, sorted);   // sorting options + 20 per page
        TableCells.bindCount(lblRowCount, filtered, catalog);

        ActionFilter<Equipment> statusFilter = new ActionFilter<>(cmbStatus, catalog, filtered, Equipment::getStatus);
        txtSearch.textProperty().addListener((obs, o, n) -> statusFilter.setSearch(e -> matches(e, n)));

        catalog.setAll(fetch());
        LiveRefresh.poll(tblCatalog, catalog, this::fetch, null);
    }

    private static TableCell<Equipment, Double> moneyCell() {
        return new TableCell<>() {
            @Override protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("\u20B1%,.2f", v));
            }
        };
    }

    private boolean matches(Equipment e, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        return has(e.getName(), q) || has(e.getCategory(), q) || has(e.getSerialNumber(), q) || has(e.getStatus(), q);
    }

    private static boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    /** Staff don't see disabled items; admins and super admins see everything. */
    private List<Equipment> fetch() {
        UserSession s = UserSession.getInstance();
        boolean staff = s == null || "STAFF".equals(s.getRoleLabel());
        return equipmentDAO.getAllEquipment().stream().filter(e -> !staff || !e.isDisabled()).toList();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        LiveRefresh.manualRefresh(btnRefresh, tblCatalog, catalog, this::fetch, null);
    }
}