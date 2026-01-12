package gui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Controller for the Subscriber Options screen.
 * Provides options for subscribers: EditPersonalInfo, ShowHistory, and restaurant services.
 * Inherits all common functionality from BaseMenuController.
 */
public class SubscriberOptionsController extends BaseMenuController {

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
     * Gets the subscriber ID.
     * 
     * @return The subscriber ID
     */
    public String getSubscriberID() {
        return subscriberID;
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
        if (subscriberID == null || subscriberID.isEmpty()) {
            showError("Subscriber ID not set. Please login again.");
            return;
        }
        showInfo("Show History - Feature coming soon!\nSubscriber ID: " + subscriberID);
        // TODO: Navigate to ShowHistory screen and fetch history from server using subscriberID
    }
    
    /**
     * Handles the "Back" button click.
     * Closes the current window to return to the previous menu.
     */
    @FXML
    private void handleBack() {
        if (backToLoginButton != null && backToLoginButton.getScene() != null) {
            Stage stage = (Stage) backToLoginButton.getScene().getWindow();
            stage.close();
        }
    }
}

