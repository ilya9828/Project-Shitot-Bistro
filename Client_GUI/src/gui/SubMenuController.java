package gui;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import common.UserSessionHelper;

/**
 * Controller for the Subscriber Menu screen.
 * Provides options for subscribers: EditPersonalInfo, ShowHistory, LogOut.
 * Inherits all common functionality from BaseMenuController.
 */
public class SubMenuController extends BaseMenuController {

    @FXML
    private Button editPersonalInfoButton;

    @FXML
    private Button showHistoryButton;

    @FXML
    private Label subscriberInfoLabel;

    private String subscriberID;

    /**
     * Sets the subscriber ID and updates the display.
     * 
     * @param subid The subscriber ID
     */
    public void setSubscriberID(String subid) {
        this.subscriberID = subid;
        if (subscriberInfoLabel != null) {
            subscriberInfoLabel.setText("Logged in as Subscriber ID: " + subid);
        }
        
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
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        if (subscriberID != null && subscriberInfoLabel != null) {
            subscriberInfoLabel.setText("Logged in as Subscriber ID: " + subscriberID);
        }
        
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
     * Handles the "Edit Personal Info" button click.
     * Navigates to the EditPersonalInfo screen.
     */
    @FXML
    private void handleEditPersonalInfo() {
        navigateToScreen("EditPersonalInfo", "Edit Personal Information");
    }

    /**
     * Handles the "Show History" button click.
     * Navigates to the ShowHistory screen.
     */
    @FXML
    private void handleShowHistory() {
        navigateToScreen("ShowHistory", "Visit History");
    }

    /**
     * Handles the "Log Out" button click.
     * Returns to the User Identification screen.
     */
    @FXML
    private void handleLogOut() {
        // Reuse the logic from base class
        handleBackToLogin();
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

