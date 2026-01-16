package gui;

import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.AlertHelper;
import common.UserSessionHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.util.List;

/**
 * Controller for the Check-In screen.
 * Allows users to check in for their reservations using confirmation codes.
 * For subscribers, automatically loads and displays their today's confirmation codes in a dropdown.
 */
public class CheckInController {

    @FXML
    private TextField confirmationCodeField;

    @FXML
    private Label statusLabel;
    
    @FXML
    private Button backButton;
    
    @FXML
    private Button qrCodeButton;
    
    @FXML
    private ComboBox<String> confirmationCodeComboBox;
    
    @FXML
    private Label selectCodeLabel;

    @FXML
    public void initialize() {
        // Check if user is a subscriber
        if (UserSessionHelper.isSubscriber()) {
            String subscriberID = UserSessionHelper.getSubscriberID();
            if (subscriberID != null) {
                // Show ComboBox and label
                selectCodeLabel.setVisible(true);
                selectCodeLabel.setManaged(true);
                confirmationCodeComboBox.setVisible(true);
                confirmationCodeComboBox.setManaged(true);
                
                // Load confirmation codes
                loadSubscriberConfirmationCodes(subscriberID);
                
                // Set up listener to auto-fill text field when code is selected
                confirmationCodeComboBox.setOnAction(e -> {
                    String selectedCode = confirmationCodeComboBox.getValue();
                    if (selectedCode != null && !selectedCode.isEmpty()) {
                        confirmationCodeField.setText(selectedCode);
                    }
                });
            }
        }
    }
    
    /**
     * Load confirmation codes for subscriber's today reservations
     */
    private void loadSubscriberConfirmationCodes(String subscriberID) {
        new Thread(() -> {
            try {
                ChatClient.subscriberConfirmationCodes.clear();
                ChatClient.expectedListType = "subscriberConfirmationCodes";
                
                HashMap<String, String> request = new HashMap<>();
                request.put("GetSubscriberTodayConfirmationCodes", subscriberID);
                
                ClientUI.chat.accept(request);
                
                // Wait for server response
                Thread.sleep(500);
                
                Platform.runLater(() -> {
                    List<String> codes = ChatClient.subscriberConfirmationCodes;
                    if (codes != null && !codes.isEmpty()) {
                        ObservableList<String> codeList = FXCollections.observableArrayList(codes);
                        confirmationCodeComboBox.setItems(codeList);
                    } else {
                        // No codes found, but keep ComboBox visible with empty list
                        ObservableList<String> emptyList = FXCollections.observableArrayList();
                        confirmationCodeComboBox.setItems(emptyList);
                        confirmationCodeComboBox.setPromptText("No reservations for today");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    // Keep ComboBox visible even on error, just show empty list
                    ObservableList<String> emptyList = FXCollections.observableArrayList();
                    confirmationCodeComboBox.setItems(emptyList);
                    confirmationCodeComboBox.setPromptText("Error loading codes");
                });
            }
        }).start();
    }

    /**
     * Handles the Check-In button click.
     * Validates the confirmation code and sends check-in request to the server.
     * Uses asynchronous communication with proper response waiting.
     */
    @FXML
    private void handleCheckIn() {
        String code = confirmationCodeField.getText().trim();

        if (code == null || code.isEmpty()) {
            AlertHelper.showError("Error", "Please enter your confirmation code.");
            return;
        }

        statusLabel.setText("Checking in... Please wait");

        HashMap<String, String> request = new HashMap<>();
        request.put("CheckIn", code);

        // Use thread for asynchronous communication with proper response waiting
        new Thread(() -> {
            // Reset response string before sending request
            ChatClient.ResetServerString();
            
            // Send request to server
            ClientUI.chat.accept(request);
            
            // Wait for server response (wait until string is no longer empty)
            // Use same pattern as other controllers: check if equals new String()
            while (ChatClient.fromserverString.equals(new String())) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                String response = ChatClient.fromserverString;
                ChatClient.ResetServerString();
                
                if (response == null) {
                    response = "";
                }

                if (response.startsWith("CheckInSuccess")) {
                    statusLabel.setText("");
                    confirmationCodeField.clear();

                    String table = "";
                    int idx = response.indexOf("TABLE=");
                    if (idx != -1) {
                        table = response.substring(idx + "TABLE=".length()).trim();
                    }

                    String msg = table.isEmpty()
                            ? "Check-In successful!"
                            : "Check-In successful!\nYour table: " + table;
                    AlertHelper.showSuccess("Success", msg);
                } 
                else if (response.equals("CheckInFailed") || response.startsWith("CheckInFailed:")) {
                    statusLabel.setText("");
                    String errorMsg = "Check-in failed.\n\n";
                    if (response.startsWith("CheckInFailed:Reservation status is not PENDING")) {
                        errorMsg += "This reservation cannot be checked in because its status is not PENDING.\n\nOnly reservations with status 'PENDING' can be checked in.";
                    } else {
                        errorMsg += "Invalid confirmation code or server error.\n\nDetails: " + response;
                    }
                    AlertHelper.showError("Error", errorMsg);
                }
                else if (response.equals("NoTableAvailable")) {
                    statusLabel.setText("");
                    AlertHelper.showError("Error", "There is currently no available table for your party.\n"
                             + "Please wait at the entrance, we will notify you as soon as a table is free.");
                }
                else if (response.isEmpty()) {
                    statusLabel.setText("");
                    AlertHelper.showError("Error", "No response from server. Please try again.");
                }
                else {
                    statusLabel.setText("");
                    AlertHelper.showError("Error", "Server error: " + response);
                }
            });
        }).start();
    }



    /**
     * Handles the Lost Code button click.
     * Opens the Lost Code recovery screen in a new window.
     */
    @FXML
    private void handleLostCode() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/gui/LostCode.fxml")
            );
            javafx.scene.Parent root = loader.load();

            Stage stage = new Stage();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            // Add CSS stylesheet
            scene.getStylesheets().add(getClass().getResource("/gui/LostCode.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Recover Confirmation Code");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showError("Error", "Failed to open Lost Code screen: " + e.getMessage());
        }
    }
    
    /**
     * Handles the Back button click.
     * Returns to the appropriate menu (Guest, Subscriber, or Staff/Manager if accessed from there).
     */
    @FXML
    private void handleBackToMenu() {
        try {
            // Use centralized navigation that handles context restoration
            UserSessionHelper.navigateBackToMenu(backButton);
        } catch (Exception e) {
            e.printStackTrace();
            AlertHelper.showError("Error", "Failed to go back to main menu: " + e.getMessage());
        }
    }
    
    /**
     * Handles the QR Code button click.
     * Displays information about QR code scanning (placeholder functionality).
     */
    @FXML
    private void handleQRCode() {
        AlertHelper.showInfo("QR Code", "Scan Your QR Code");
    }
}
