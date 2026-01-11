package gui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

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
    }

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        if (subscriberID != null && subscriberInfoLabel != null) {
            subscriberInfoLabel.setText("Logged in as Subscriber ID: " + subscriberID);
        }
    }

    /**
     * Handles the "Edit Personal Info" button click.
     * TODO: Implement EditPersonalInfo functionality.
     */
    @FXML
    private void handleEditPersonalInfo() {
        showInfo("Edit Personal Info - Feature coming soon!\nSubscriber ID: " + subscriberID);
        // TODO: Navigate to EditPersonalInfo screen
    }

    /**
     * Handles the "Show History" button click.
     * TODO: Implement ShowHistory functionality.
     */
    @FXML
    private void handleShowHistory() {
        showInfo("Show History - Feature coming soon!\nSubscriber ID: " + subscriberID);
        // TODO: Navigate to ShowHistory screen and fetch history from server
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
}

