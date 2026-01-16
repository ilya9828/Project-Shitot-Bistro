package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.AlertHelper;
import common.UserSessionHelper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * This class controls the Register New Subscriber screen.
 * Allows staff to register new subscribers with their details.
 */
public class RegisterNewSubscriberController {

    @FXML
    private TextField fullNameTextField;

    @FXML
    private TextField phoneTextField;

    @FXML
    private TextField subscriberIdTextField;

    @FXML
    private TextField emailTextField;

    @FXML
    private Button saveButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Button btnBack;

    /**
     * Initialize the controller - set up listeners and disable subscriber ID field
     */
    @FXML
    public void initialize() {
        // Disable and grey out the subscriber ID text field
        subscriberIdTextField.setEditable(false);
        
        // Add listener to phone number text field to auto-fill subscriber ID
        phoneTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty()) {
                subscriberIdTextField.setText(newValue);
            } else {
                subscriberIdTextField.setText("");
            }
        });
    }

    /**
     * Handle the Save button click - validates and saves subscriber to database
     */
    @FXML
    private void handleSave() {
        // Get values from text fields
        String fullName = fullNameTextField.getText().trim();
        String phone = phoneTextField.getText().trim();
        String subscriberId = subscriberIdTextField.getText().trim();
        String email = emailTextField.getText().trim();

        // Validate all fields are filled
        if (fullName.isEmpty() || phone.isEmpty() || email.isEmpty()) {
            statusLabel.setText("Please fill all fields.");
            AlertHelper.showError("Error", "Please fill all required fields.");
            return;
        }

        // Validate email format
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            statusLabel.setText("Invalid email format.");
            AlertHelper.showError("Error", "Please enter a valid email address.");
            return;
        }

        // Validate phone number format (basic validation)
        if (!phone.matches("\\d{10,12}")) {
            statusLabel.setText("Invalid phone number format.");
            AlertHelper.showError("Error", "Please enter a valid phone number (10-12 digits).");
            return;
        }

        // Ensure subscriber ID matches phone number
        if (!subscriberId.equals(phone)) {
            subscriberId = phone; // Force it to match
            subscriberIdTextField.setText(phone);
        }

        statusLabel.setText("Saving subscriber... Please wait...");

        // Create request to send to server
        HashMap<String, String> subscriberData = new HashMap<>();
        subscriberData.put("RegisterNewSubscriber", fullName + "|" + phone + "|" + subscriberId + "|" + email);

        // Send to server (this blocks until response is received)
        ClientUI.chat.accept(subscriberData);

        // Check the response
        String response = ChatClient.fromserverString;
        if ("SubscriberAdded".equals(response)) {
            statusLabel.setText("");
            AlertHelper.showSuccess("Success", "Subscriber registered successfully!");
            // Clear all fields after successful save
            clearFields();
        } else if ("SubscriberExists".equals(response)) {
            statusLabel.setText("");
            AlertHelper.showError("Error", "Subscriber with this phone number already exists.");
        } else {
            statusLabel.setText("");
            // Show more detailed error message
            String errorMsg = "Failed to register subscriber. ";
            if (response != null && !response.isEmpty()) {
                errorMsg += "Server response: " + response;
            } else {
                errorMsg += "Please try again.";
            }
            AlertHelper.showError("Error", errorMsg);
        }

        ChatClient.ResetServerString();
    }

    /**
     * Clear all input fields after successful save
     */
    private void clearFields() {
        fullNameTextField.setText("");
        phoneTextField.setText("");
        subscriberIdTextField.setText("");
        emailTextField.setText("");
    }

    /**
     * Handles the Back button click.
     * Closes the current screen and navigates back to the appropriate menu.
     * 
     * @param event The click event on the back button
     * @throws IOException If navigation fails
     */
    public void Back(ActionEvent event) throws IOException {
        UserSessionHelper.navigateBackToMenu((Node) event.getSource());
    }


    // Alert methods removed - now using AlertHelper static methods
}

