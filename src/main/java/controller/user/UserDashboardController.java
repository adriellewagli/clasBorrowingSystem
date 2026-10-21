package controller.user;

import controller.BaseController;
import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import model.UserSession;

import java.io.IOException;

public class UserDashboardController extends BaseController {

    private static final String FXML_ROOT = "/com/borrowclas/clasborrowingsystem/fxml/";

    // --- Header & Sidebar ---
    @FXML private Label lblUserAvatar;
    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;
    @FXML private Button btnNavCatalog;
    @FXML private Button btnNavMyBorrows;
    @FXML private Button btnNavMyRequests;
    @FXML private Button btnNavSettings;

    // --- Center content (each tab is loaded into this) ---
    @FXML private StackPane dynamicContentArea;

    private Node settingsView = null;

    // Navigation guards
    private boolean isNavigating = false;
    private Button currentActiveButton = null;

    @FXML
    public void initialize() {
        javafx.application.Platform.runLater(() -> {
            if (lblUserName != null && lblUserName.getScene() != null) {
                lblUserName.getScene().setUserData(this);
            }
        });

        if (UserSession.getInstance() != null) {
            refreshHeaderProfile();
        }

        // the first tab: Borrow Equipment
        openView("user/borrow_equipment.fxml", btnNavCatalog);
    }

    public void refreshHeaderProfile() {
        UserSession session = UserSession.getInstance();
        if (session == null) return;
        if (lblUserName != null) {
            lblUserName.setText(session.getFullName() != null ? session.getFullName() : "User");
        }
        if (lblUserRole != null) {
            lblUserRole.setText(session.getRoleLabel());
        }
        if (lblUserAvatar != null && session.getFullName() != null && !session.getFullName().isEmpty()) {
            String[] names = session.getFullName().split(" ");
            String initials = names.length > 1
                    ? ("" + names[0].charAt(0) + names[names.length - 1].charAt(0)).toUpperCase()
                    : ("" + names[0].charAt(0)).toUpperCase();
            lblUserAvatar.setText(initials);
        }
    }

    // ------------------------------------------------------------ navigation

    @FXML
    private void handleViewCatalog(ActionEvent event) {
        openView("user/borrow_equipment.fxml", btnNavCatalog);
    }

    @FXML
    private void handleViewMyBorrows(ActionEvent event) {
        openView("user/my_borrows.fxml", btnNavMyBorrows);
    }

    @FXML
    private void handleViewMyRequests(ActionEvent event) {
        openView("user/current_requests.fxml", btnNavMyRequests);
    }

    @FXML
    private void handleViewSettings(ActionEvent event) {
        if (isNavigating || currentActiveButton == btnNavSettings) return;
        try {
            if (settingsView == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_ROOT + "user/user_settings.fxml"));
                settingsView = loader.load();
            }
            updateActiveSidebarButton(btnNavSettings);
            switchViewWithLock(settingsView);
        } catch (IOException e) {
            System.err.println("[UserDashboardController] Error loading user_settings.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Loads a tab fresh every time, so it always shows current data. */
    private void openView(String fxmlPath, Button navButton) {
        if (isNavigating || currentActiveButton == navButton) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_ROOT + fxmlPath));
            Node view = loader.load();
            updateActiveSidebarButton(navButton);
            switchViewWithLock(view);
        } catch (Exception e) {
            System.err.println("[UserDashboardController] Error loading " + fxmlPath + ": " + e.getMessage());
            e.printStackTrace();
            // don't leave the sidebar stuck on a tab that never opened
            currentActiveButton = null;
            isNavigating = false;
            showErrorDialog("Could not open this tab", String.valueOf(e.getMessage()));
        }
    }

    private void switchViewWithLock(Node targetView) {
        if (dynamicContentArea == null || targetView == null) return;

        isNavigating = true;
        dynamicContentArea.getChildren().setAll(targetView);

        FadeTransition fade = new FadeTransition(Duration.millis(200), targetView);
        fade.setFromValue(0.3);
        fade.setToValue(1.0);
        fade.setOnFinished(e -> isNavigating = false);
        fade.play();
    }

    private void updateActiveSidebarButton(Button activeButton) {
        currentActiveButton = activeButton;
        Button[] buttons = {btnNavCatalog, btnNavMyBorrows, btnNavMyRequests, btnNavSettings};
        for (Button btn : buttons) {
            if (btn != null) {
                btn.getStyleClass().remove("sidebar-btn-active");
                if (!btn.getStyleClass().contains("sidebar-btn")) btn.getStyleClass().add("sidebar-btn");
            }
        }
        if (activeButton != null) {
            activeButton.getStyleClass().remove("sidebar-btn");
            if (!activeButton.getStyleClass().contains("sidebar-btn-active")) activeButton.getStyleClass().add("sidebar-btn-active");
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) throws IOException {
        BorrowEquipmentController.clearQueue();
        UserSession.cleanUserSession();
        navigateTo("/com/borrowclas/clasborrowingsystem/fxml/auth/login.fxml");
    }
}
