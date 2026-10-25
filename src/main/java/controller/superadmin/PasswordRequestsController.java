package controller.superadmin;

import controller.dialog.ConfirmDialogController;
import dao.EquipmentDAO;
import dao.EquipmentDAOImpl;
import db.DatabaseConnection;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import model.PasswordTicket;
import model.UserSession;
import util.TableCells;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import javafx.util.Duration;


import javafx.stage.StageStyle;
import javafx.stage.Window;
public class PasswordRequestsController {

    @FXML private TextField txtSearch;
    @FXML private TableView<PasswordTicket> tblTickets;
    @FXML private TableColumn<PasswordTicket, Integer> colTicketId;
    @FXML private TableColumn<PasswordTicket, String> colUsername;
    @FXML private TableColumn<PasswordTicket, String> colFullName;
    @FXML private TableColumn<PasswordTicket, LocalDateTime> colRequestDate;
    @FXML private TableColumn<PasswordTicket, Void> colAction;

    private final EquipmentDAO equipmentDAO = new EquipmentDAOImpl();
    private final ObservableList<PasswordTicket> ticketList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (colTicketId != null) colTicketId.setCellValueFactory(new PropertyValueFactory<>("ticketId"));
        if (colUsername != null) colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        if (colFullName != null) colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        if (colRequestDate != null) colRequestDate.setCellValueFactory(new PropertyValueFactory<>("requestDate"));

        if (colAction != null) {
            colAction.setCellFactory(TableCells.actionButton("Approve", ticket -> handleApprove(ticket)));
        }

        if (tblTickets != null) {
            TableCells.modernize(tblTickets);
        }
        FilteredList<PasswordTicket> filtered = new FilteredList<>(ticketList, t -> true);
        if (txtSearch != null) {
            txtSearch.textProperty().addListener((obs, o, n) -> filtered.setPredicate(t -> {
                if (n == null || n.isBlank()) return true;
                String f = n.toLowerCase().trim();
                return String.valueOf(t.getTicketId()).contains(f)
                        || has(t.getUsername(), f)
                        || has(t.getFullName(), f);
            }));
        }
        if (tblTickets != null) {
            SortedList<PasswordTicket> sorted = new SortedList<>(filtered);
            sorted.comparatorProperty().bind(tblTickets.comparatorProperty());
            util.TablePager.install(tblTickets, filtered, sorted);
        }
        loadTickets();
    }

    private static boolean has(String v, String f) {
        return v != null && v.toLowerCase().contains(f);
    }

    @FXML
    public void loadTickets() {
        ticketList.setAll(equipmentDAO.getPendingPasswordTickets());
    }

    private void handleApprove(PasswordTicket ticket) {
        if (ticket == null) return;
        UserSession session = UserSession.getInstance();
        int adminId = session != null ? session.getUserId() : 1;

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/superadmin/approve_password_dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(javafx.stage.StageStyle.TRANSPARENT);

            Scene scene = new Scene(root, 480, 310);
            scene.setFill(Color.TRANSPARENT);
            dialogStage.setScene(scene);

            if (tblTickets != null && tblTickets.getScene() != null) {
                dialogStage.initOwner(tblTickets.getScene().getWindow());
            }

            ApprovePasswordDialogController dialogController = loader.getController();
            dialogController.setDialogStage(dialogStage);
            dialogController.initData(ticket.getUsername(), ticket.getUsername() + "123!");

            scene.getRoot().setOpacity(0);
            dialogStage.setOnShown(e -> {
                Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
                dialogStage.setX(screenBounds.getMinX() + (screenBounds.getWidth() - dialogStage.getWidth()) / 2.0);
                dialogStage.setY(screenBounds.getMinY() + (screenBounds.getHeight() - dialogStage.getHeight()) / 2.0);

                // Use dialogStage.getScene().getRoot() so it doesn't rely on the local 'scene' variable
                FadeTransition ft = new FadeTransition(Duration.millis(200), dialogStage.getScene().getRoot());
                ft.setFromValue(0.0);
                ft.setToValue(1.0);
                ft.play();
            });

            dialogStage.showAndWait();

            if (dialogController.isConfirmed()) {
                String newPassword = dialogController.getResultPassword();

                boolean passwordUpdated = updatePasswordForUser(ticket.getUserId(), newPassword);
                if (passwordUpdated) {
                    boolean success = equipmentDAO.updatePasswordTicketStatus(ticket.getTicketId(), "APPROVED", adminId);
                    if (success) {
                        // Show your success dialog!
                        Window owner = tblTickets != null && tblTickets.getScene() != null ? tblTickets.getScene().getWindow() : null;

                        try {
                            FXMLLoader successLoader = new FXMLLoader(getClass().getResource("/com/borrowclas/clasborrowingsystem/fxml/dialog/success_dialog.fxml"));
                            Parent successRoot = successLoader.load();

                            Stage successStage = new Stage();
                            successStage.initModality(Modality.APPLICATION_MODAL);
                            successStage.initStyle(StageStyle.TRANSPARENT);
                            successStage.initOwner(owner);

                            Scene successScene = new Scene(successRoot, 440, 240);
                            successScene.setFill(Color.TRANSPARENT);
                            successStage.setScene(successScene);

                            // Get controller if needed to set custom messages
                            // SuccessDialogController successController = successLoader.getController();
                            // successController.setDetails("Success!", "Password reset approved successfully.");

                            successRoot.setOpacity(0);
                            successStage.setOnShown(e -> {
                                Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
                                successStage.setX(screenBounds.getMinX() + (screenBounds.getWidth() - successStage.getWidth()) / 2.0);
                                successStage.setY(screenBounds.getMinY() + (screenBounds.getHeight() - successStage.getHeight()) / 2.0);

                                FadeTransition ft = new FadeTransition(Duration.millis(200), successRoot);
                                ft.setFromValue(0.0);
                                ft.setToValue(1.0);
                                ft.play();
                            });

                            successStage.showAndWait();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }

                        loadTickets();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean updatePasswordForUser(int userId, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}