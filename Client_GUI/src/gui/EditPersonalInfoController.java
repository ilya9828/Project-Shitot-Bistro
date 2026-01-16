package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.AlertHelper;
import common.UserSessionHelper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controller for the Edit Personal Information screen.
 * Allows subscribers to edit their name, email, and phone number.
 */
public class EditPersonalInfoController {

    @FXML
    private Label lblSubscriberID;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblErr;

    @FXML
    private TextField txtName;
    @FXML
    private TextField txtEmail;
    @FXML
    private TextField txtPhone;

    @FXML
    private Button btnUpdate;
    @FXML
    private Button btnBack;

    private String subscriberID;

    /**
     * Initializes the controller and loads subscriber information.
     */
    @FXML
    public void initialize() {
        subscriberID = UserSessionHelper.getSubscriberID();
        if (subscriberID != null) {
            lblSubscriberID.setText("Subscriber ID: " + subscriberID);
            loadSubscriberInfo();
        } else {
            lblErr.setText("Error: No subscriber ID found. Please log in again.");
        }
    }

    /**
     * Loads subscriber information from the server.
     */
    private void loadSubscriberInfo() {
        lblErr.setText("");
        lblStatus.setText("Loading subscriber information...");
        btnUpdate.setDisable(true);

        new Thread(() -> {
            try {
                HashMap<String, String> request = new HashMap<>();
                request.put("GetSubscriberInfo", subscriberID);
                ClientUI.chat.accept(request);

                // Wait for server response
                Thread.sleep(500);

                Platform.runLater(() -> {
                    String response = ChatClient.fromserverString;
                    ChatClient.ResetServerString();

                    if (response != null && !response.equals("Error") && !response.isEmpty()) {
                        // Response format: "name|email|phone"
                        String[] parts = response.split("\\|");
                        if (parts.length == 3) {
                            txtName.setText(parts[0] != null && !parts[0].equals("null") ? parts[0] : "");
                            txtEmail.setText(parts[1] != null && !parts[1].equals("null") ? parts[1] : "");
                            txtPhone.setText(parts[2] != null && !parts[2].equals("null") ? parts[2] : "");
                            lblStatus.setText("");
                            btnUpdate.setDisable(false);
                        } else {
                            lblErr.setText("Error: Invalid response from server.");
                            lblStatus.setText("");
                            btnUpdate.setDisable(false);
                        }
                    } else {
                        lblErr.setText("Error: Could not load subscriber information.");
                        lblStatus.setText("");
                        btnUpdate.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblErr.setText("Error loading subscriber information: " + e.getMessage());
                    lblStatus.setText("");
                    btnUpdate.setDisable(false);
                    e.printStackTrace();
                });
            }
        }).start();
    }

    /**
     * Handles the Update button click.
     * Validates input and sends update request to server.
     */
    @FXML
    private void UpdateBtn(ActionEvent event) {
        String name = txtName.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        // Validation
        if (name.isEmpty()) {
            AlertHelper.showError("Error", "Name cannot be empty.");
            return;
        }

        if (email.isEmpty() && phone.isEmpty()) {
            AlertHelper.showError("Error", "Please provide at least email or phone number.");
            return;
        }

        // Basic email validation
        if (!email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            AlertHelper.showError("Error", "Please enter a valid email address.");
            return;
        }

        // Basic phone validation (8-15 digits)
        if (!phone.isEmpty() && !phone.matches("\\d{8,15}")) {
            AlertHelper.showError("Error", "Please enter a valid phone number (8-15 digits).");
            return;
        }

        lblErr.setText("");
        lblStatus.setText("Updating information...");
        btnUpdate.setDisable(true);

        new Thread(() -> {
            try {
                // Format: "subscriberID|name|email|phone"
                String updateData = subscriberID + "|" + name + "|" + email + "|" + phone;
                
                HashMap<String, String> request = new HashMap<>();
                request.put("UpdateSubscriberInfo", updateData);
                ClientUI.chat.accept(request);

                // Wait for server response
                Thread.sleep(500);

                Platform.runLater(() -> {
                    String response = ChatClient.fromserverString;
                    ChatClient.ResetServerString();

                    if ("Updated".equals(response)) {
                        lblStatus.setText("Information updated successfully!");
                        lblStatus.setStyle("-fx-text-fill: #4ade80;");
                        btnUpdate.setDisable(false);
                    } else {
                        lblErr.setText("Error: Failed to update information. " + (response != null ? response : ""));
                        lblStatus.setText("");
                        btnUpdate.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblErr.setText("Error updating information: " + e.getMessage());
                    lblStatus.setText("");
                    btnUpdate.setDisable(false);
                    e.printStackTrace();
                });
            }
        }).start();
    }

    /**
     * Handles the Back button click.
     * Returns to the appropriate menu (Subscriber Menu, or Staff/Manager Menu if accessed from there).
     */
    @FXML
    private void Back(ActionEvent event) throws IOException {
        // Use centralized navigation that handles context restoration
        UserSessionHelper.navigateBackToMenu((javafx.scene.Node) event.getSource());
    }

    // Alert methods removed - now using AlertHelper static methods
}

