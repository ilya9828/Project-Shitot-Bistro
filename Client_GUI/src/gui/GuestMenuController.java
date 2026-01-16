package gui;

import java.io.IOException;

import javafx.fxml.FXML;
import common.UserSessionHelper;

/**
 * Controller for the Guest Menu screen.
 * Provides navigation to guest functions: ReserveTable, EditReservation, CheckIn,
 * JoinWaitingList, ExitWaitingList, PayBill.
 * Inherits all common functionality from BaseMenuController.
 */
public class GuestMenuController extends BaseMenuController {
    
    /**
     * Initializes the controller.
     * Updates button text based on whether we came from Staff/Manager.
     */
    @FXML
    public void initialize() {
        // Update button text based on whether we came from Staff/Manager
        if (backToLoginButton != null) {
            if (UserSessionHelper.hasOriginalContext()) {
                backToLoginButton.setText("Back");
            } else {
                backToLoginButton.setText("Back to Login");
            }
        }
    }
    
    /**
     * Overrides handleBackToLogin to check if we came from Staff/Manager menu.
     * If so, navigate back to that menu (with fromMenu=true to restore context).
     * Otherwise, go to login.
     */
    @Override
    @FXML
    protected void handleBackToLogin() {
        try {
            // Check if we came from Staff/Manager menu
            if (UserSessionHelper.hasOriginalContext()) {
                // Navigate back to Staff/Manager menu (fromMenu=true restores context)
                UserSessionHelper.navigateBackToMenu(backToLoginButton, true);
            } else {
                // Normal flow: go to login
                super.handleBackToLogin();
            }
        } catch (IOException e) {
            System.err.println("Failed to navigate back: " + e.getMessage());
            e.printStackTrace();
            // Fallback to normal login
            super.handleBackToLogin();
        }
    }
}

