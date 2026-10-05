package controller.superadmin;

import controller.BaseController;
import dao.UserDAO;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.UserAccount;
import model.UserSession;
import util.DialogKit;
import util.TableCells;

import java.util.Optional;
import java.util.regex.Pattern;

/** Super Admin only: create, activate, deactivate and delete accounts. */
public class AccountManagementController extends BaseController {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{4,30}");
    private static final String ALL_ROLES = "All roles";

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbRoleFilter;
    @FXML private VBox tableCard;
    @FXML private Label lblRowCount;
    @FXML private TableView<UserAccount> tblAccounts;
    @FXML private TableColumn<UserAccount, Integer> colId;
    @FXML private TableColumn<UserAccount, String> colFullName;
    @FXML private TableColumn<UserAccount, String> colUsername;
    @FXML private TableColumn<UserAccount, String> colRole;
    @FXML private TableColumn<UserAccount, String> colStatus;
    @FXML private TableColumn<UserAccount, String> colCredentials;
    @FXML private TableColumn<UserAccount, Void> colActions;

    private final UserDAO userDAO = new UserDAO();
    private final ObservableList<UserAccount> accounts = FXCollections.observableArrayList();
    private FilteredList<UserAccount> filtered;

    @FXML
    public void initialize() {
        if (!isSuperAdmin()) {
            // Only reachable if someone loads this screen without the role - show nothing.
            tblAccounts.setDisable(true);
            return;
        }

        setupColumns();
        setupFilters();
        loadAccounts();
    }

    private boolean isSuperAdmin() {
        UserSession s = UserSession.getInstance();
        return s != null && s.isSuperAdmin();
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("userId"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("roleLabel"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCredentials.setCellValueFactory(new PropertyValueFactory<>("credentialState"));

        colId.setCellFactory(TableCells.<UserAccount, Integer>idCell());
        colFullName.setCellFactory(TableCells.avatarNameCell());
        colUsername.setCellFactory(TableCells.monoCell());
        colRole.setCellFactory(TableCells.primaryCell());
        colStatus.setCellFactory(TableCells.statusPill());
        colCredentials.setCellFactory(TableCells.mutedCell());
        colActions.setCellFactory(col -> new ActionCell());

        TableCells.modernize(tblAccounts);
        if (tableCard != null) TableCells.clipRounded(tableCard, 14);
    }

    private void setupFilters() {
        filtered = new FilteredList<>(accounts, a -> true);
        SortedList<UserAccount> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tblAccounts.comparatorProperty());
        tblAccounts.setItems(sorted);
        TableCells.bindCount(lblRowCount, filtered, accounts);

        cmbRoleFilter.setItems(FXCollections.observableArrayList(ALL_ROLES, "User", "Admin", "Super Admin"));
        cmbRoleFilter.setValue(ALL_ROLES);

        txtSearch.textProperty().addListener((obs, o, n) -> applyFilters());
        cmbRoleFilter.valueProperty().addListener((obs, o, n) -> applyFilters());
    }

    private void applyFilters() {
        String q = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String role = cmbRoleFilter.getValue();

        filtered.setPredicate(a ->
                (role == null || ALL_ROLES.equals(role) || role.equalsIgnoreCase(a.getRoleLabel()))
                        && (q.isEmpty()
                        || contains(a.getFullName(), q)
                        || contains(a.getUsername(), q)
                        || contains(a.getRoleLabel(), q)
                        || contains(a.getStatus(), q)));
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    private void loadAccounts() {
        accounts.setAll(userDAO.getAllUsers());
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        if (isSuperAdmin()) loadAccounts();
    }

    // ------------------------------------------------------------------ create

    @FXML
    private void handleCreateAccount(ActionEvent event) {
        if (!isSuperAdmin()) return;

        Label title = new Label("Create Account");
        title.getStyleClass().add("modal-title");
        Label subtitle = new Label("A default username and password are filled in. The account holder is asked to change "
                + "them when they first sign in, and can keep them if they prefer.");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMinHeight(Region.USE_PREF_SIZE);

        TextField txtName = DialogKit.textField("e.g. Juan Dela Cruz");
        ComboBox<String> cmbRole = new ComboBox<>(FXCollections.observableArrayList("USER", "ADMIN", "SUPERADMIN"));
        cmbRole.setValue("USER");
        cmbRole.setMaxWidth(Double.MAX_VALUE);
        cmbRole.setPrefHeight(40);
        TextField txtUser = DialogKit.textField("Default username");
        txtUser.setText(userDAO.generateUsername("USER"));
        TextField txtPass = DialogKit.textField("Default password");
        txtPass.setText(userDAO.generateDefaultPassword());

        cmbRole.valueProperty().addListener((obs, o, n) -> {
            if (n != null) txtUser.setText(userDAO.generateUsername(n));
        });

        Label message = DialogKit.message();

        Button create = new Button("Create Account");
        create.getStyleClass().add("btn-primary");
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(10, spacer, cancel, create);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = DialogKit.form(460);
        root.getChildren().addAll(
                title, subtitle,
                DialogKit.field("Full name", txtName),
                DialogKit.field("Role", cmbRole),
                DialogKit.field("Default username", txtUser),
                DialogKit.field("Default password", txtPass),
                message, buttons);

        Stage stage = DialogKit.modal(tblAccounts.getScene().getWindow(), "Create Account", root);
        cancel.setOnAction(e -> stage.close());

        create.setOnAction(e -> {
            if (!isSuperAdmin()) return;

            String name = txtName.getText() == null ? "" : txtName.getText().trim();
            String role = cmbRole.getValue();
            String username = txtUser.getText() == null ? "" : txtUser.getText().trim();
            String password = txtPass.getText() == null ? "" : txtPass.getText();

            if (name.isEmpty()) {
                DialogKit.showMessage(message, "Enter the account holder's full name.", false);
                return;
            }
            if (role == null) {
                DialogKit.showMessage(message, "Choose a role.", false);
                return;
            }
            if (!USERNAME.matcher(username).matches()) {
                DialogKit.showMessage(message, "Username must be 4-30 characters: letters, numbers, dot, dash or underscore.", false);
                return;
            }
            if (password.length() < 6 || !password.equals(password.trim())) {
                DialogKit.showMessage(message, "Password must be at least 6 characters and can't start or end with a space.", false);
                return;
            }

            String error = userDAO.createUser(name, username, password, role);
            if (error != null) {
                DialogKit.showMessage(message, error, false);
                return;
            }

            stage.close();
            loadAccounts();
            String summary = name + " (" + UserDAO.normalizeRole(role) + ")\nUsername: " + username
                    + "\nPassword: " + password + "\nShare these with the account holder.";
            // Wait until the Create Account popup has fully closed before opening the next modal.
            Platform.runLater(() -> showSuccessDialog("Account Created", summary));
        });

        stage.showAndWait();
    }

    // ------------------------------------------------------------------ activate / deactivate / delete

    private void toggleActive(UserAccount account) {
        if (account == null || !isSuperAdmin()) return;

        boolean activate = !account.isActive();
        if (!activate && !confirm("Deactivate account",
                "Deactivate '" + account.getUsername() + "'? They won't be able to sign in until you activate it again.")) {
            return;
        }

        String error = userDAO.setActive(account.getUserId(), activate, UserSession.getInstance().getUserId());
        if (error != null) {
            showErrorDialog("Action Not Allowed", error);
        }
        loadAccounts();
    }

    private void deleteAccount(UserAccount account) {
        if (account == null || !isSuperAdmin()) return;

        if (!confirm("Delete account",
                "Permanently delete '" + account.getUsername() + "' (" + account.getFullName() + ")? This cannot be undone.")) {
            return;
        }

        String error = userDAO.deleteUser(account.getUserId(), UserSession.getInstance().getUserId());
        if (error != null) {
            showErrorDialog("Can't Delete Account", error);
        }
        loadAccounts();
    }

    private boolean confirm(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (tblAccounts.getScene() != null) alert.initOwner(tblAccounts.getScene().getWindow());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /** ACTIONS column: Activate/Deactivate + Delete. Your own row is locked. */
    private class ActionCell extends TableCell<UserAccount, Void> {
        private final Button toggle = new Button();
        private final Button delete = new Button("Delete");
        private final HBox box = new HBox(8, toggle, delete);

        ActionCell() {
            toggle.getStyleClass().add("btn-table-action");
            delete.getStyleClass().add("btn-table-danger");
            box.setAlignment(Pos.CENTER_LEFT);
            toggle.setOnAction(e -> toggleActive(rowAccount()));
            delete.setOnAction(e -> deleteAccount(rowAccount()));
        }

        private UserAccount rowAccount() {
            int i = getIndex();
            return (i >= 0 && i < getTableView().getItems().size()) ? getTableView().getItems().get(i) : null;
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            UserAccount a = empty ? null : rowAccount();
            if (a == null) {
                setGraphic(null);
                return;
            }
            toggle.setText(a.isActive() ? "Deactivate" : "Activate");
            boolean self = a.getUserId() == UserSession.getInstance().getUserId();
            toggle.setDisable(self);
            delete.setDisable(self);
            setGraphic(box);
        }
    }
}
