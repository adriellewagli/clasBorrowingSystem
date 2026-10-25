package controller.admin;

import controller.BaseController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import model.ItemUsage;

import java.util.ArrayList;
import java.util.List;
import util.ActionFilter;
import util.LiveRefresh;
import util.TableCells;

/** "Most Used Items": which equipment is borrowed the most. */
public class ItemUsageController extends BaseController {

    @FXML private Label lblTopItem;
    @FXML private Label lblTotalBorrows;
    @FXML private BarChart<String, Number> chartTop;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbActionFilter;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;
    @FXML private TableView<ItemUsage> tblUsage;
    @FXML private TableColumn<ItemUsage, Integer> colRank;
    @FXML private TableColumn<ItemUsage, String> colName;
    @FXML private TableColumn<ItemUsage, String> colCategory;
    @FXML private TableColumn<ItemUsage, String> colSerial;
    @FXML private TableColumn<ItemUsage, Integer> colTimes;
    @FXML private TableColumn<ItemUsage, String> colLast;
    @FXML private TableColumn<ItemUsage, String> colStatus;
    @FXML private ComboBox<String> cmbPeriod;
    @FXML private Label lblChartTitle;

    private volatile String periodKey = "ALL";

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<ItemUsage> usage = FXCollections.observableArrayList();
    private FilteredList<ItemUsage> filtered;
    private ActionFilter<ItemUsage> categoryFilter;

    @FXML
    public void initialize() {
        colRank.setCellValueFactory(new PropertyValueFactory<>("rank"));
        colName.setCellValueFactory(new PropertyValueFactory<>("equipmentName"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colTimes.setCellValueFactory(new PropertyValueFactory<>("timesBorrowed"));
        colLast.setCellValueFactory(new PropertyValueFactory<>("lastBorrowedText"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("currentStatus"));

        colRank.setCellFactory(TableCells.idCell());
        colName.setCellFactory(TableCells.primaryCell());
        colCategory.setCellFactory(TableCells.mutedCell());
        colSerial.setCellFactory(TableCells.monoCell());
        colTimes.setCellFactory(TableCells.primaryCell());
        colLast.setCellFactory(TableCells.mutedCell());
        colStatus.setCellFactory(TableCells.statusPill());

        TableCells.modernize(tblUsage);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);

        filtered = new FilteredList<>(usage, u -> true);
        SortedList<ItemUsage> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblUsage.comparatorProperty());
        util.TablePager.install(tblUsage, filtered, sorted);
        TableCells.bindCount(lblRowCount, filtered, usage);

        // the "action" filter here narrows the ranking by category
        categoryFilter = new ActionFilter<>(cmbActionFilter, usage, filtered, ItemUsage::getCategory);
        txtSearch.textProperty().addListener((obs, o, n) -> categoryFilter.setSearch(u -> {
            if (n == null || n.isBlank()) return true;
            String q = n.toLowerCase().trim();
            return has(u.getEquipmentName(), q) || has(u.getCategory(), q)
                    || has(u.getSerialNumber(), q) || has(u.getCurrentStatus(), q);
        }));

        chartTop.setLegendVisible(false);
        chartTop.setAnimated(false);

        cmbPeriod.setItems(FXCollections.observableArrayList("Today", "This week", "This month", "This year", "All time"));
        cmbPeriod.setValue("All time");
        cmbPeriod.valueProperty().addListener((o, a, b) -> {
            periodKey = switch (b == null ? "" : b) {
                case "Today" -> "DAY";
                case "This week" -> "WEEK";
                case "This month" -> "MONTH";
                case "This year" -> "YEAR";
                default -> "ALL";
            };
            if (lblChartTitle != null) lblChartTitle.setText("Top 5 most borrowed \u2014 " + (b == null ? "All time" : b));
            load();
        });
        load();
        // borrows approved / returned while this page is open update the ranking and chart by themselves
        LiveRefresh.poll(tblUsage, usage, this::fetchUsage, this::showSummary);
    }

    private static boolean has(String v, String q) {
        return v != null && v.toLowerCase().contains(q);
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        load();
    }

    /** Reads the ranking straight from the transactions table (database only - safe off the UI thread). */
    private List<ItemUsage> fetchUsage() {
        return equipmentDAO.getMostUsedEquipment(periodKey);
    }

    private void load() {
        usage.setAll(fetchUsage());
        showSummary();
    }

    /** Top-line numbers and the bar chart - redrawn whenever the ranking changes. */
    private void showSummary() {
        long total = usage.stream().mapToLong(ItemUsage::getTimesBorrowed).sum();
        lblTotalBorrows.setText(String.valueOf(total));
        ItemUsage top = usage.isEmpty() ? null : usage.get(0);
        lblTopItem.setText(top == null || top.getTimesBorrowed() == 0 ? "No borrows yet"
                : top.getEquipmentName() + "  (" + top.getTimesBorrowed() + "x)");

        // chart: the five most borrowed items
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        List<String> labels = new ArrayList<>();
        int max = 0;
        for (ItemUsage u : usage) {
            if (u.getTimesBorrowed() <= 0 || labels.size() >= 5) break;
            // two items can share a name (e.g. three "Dell Laptop"); the chart needs unique labels
            String label = u.getEquipmentName();
            if (labels.contains(label)) label = label + " (" + u.getSerialNumber() + ")";
            int n = 2;
            while (labels.contains(label)) label = u.getEquipmentName() + " #" + n++;
            labels.add(label);
            series.getData().add(new XYChart.Data<>(label, u.getTimesBorrowed()));
            max = Math.max(max, u.getTimesBorrowed());
        }
        // reset the axis categories too, otherwise labels from the previous ranking linger
        chartTop.getData().clear();
        xAxis.setCategories(FXCollections.observableArrayList(labels));
        chartTop.getData().add(series);

        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(Math.max(5, max + 1));
        yAxis.setTickUnit(Math.max(1, Math.ceil(Math.max(5, max + 1) / 10.0)));
        yAxis.setMinorTickCount(0);
    }
}
