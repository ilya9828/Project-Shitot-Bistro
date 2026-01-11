package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for the User Identification screen.
 * Handles subscriber login validation and guest navigation.
 */
public class UserIdentificationController {

    @FXML
    private TextField subidField;

    @FXML
    private Button loginButton;

    @FXML
    private Button continueAsGuestButton;

    @FXML
    private Label statusLabel;

    /**
     * Handles the "Login as Subscriber" button click.
     * Validates the SUBID with the server and navigates to Subscriber Menu if valid.
     */
    @FXML
    private void handleLoginAsSubscriber() {
        String subid = subidField.getText();

        // Validate that SUBID is not empty
        if (subid == null || subid.trim().isEmpty()) {
            showError("Please enter ID.");
            return;
        }

        // Disable buttons during validation
        loginButton.setDisable(true);
        continueAsGuestButton.setDisable(true);
        statusLabel.setText("Validating ID... Please wait...");

        // Send SUBID to server for validation
        new Thread(() -> {
            try {
                HashMap<String, String> loginRequest = new HashMap<>();
                loginRequest.put("ValidateSubscriber", subid.trim());
                
                ClientUI.chat.accept(loginRequest);

                // Wait for server response
                Thread.sleep(500);

                Platform.runLater(() -> {
                    String response = ChatClient.fromserverString;
                    ChatClient.ResetServerString();

                    if ("SubscriberValid".equals(response)) {
                        // Login successful - navigate to Subscriber Menu
                        statusLabel.setText("");
                        UserSessionHelper.setSubscriber(subid.trim());
                        navigateToSubscriberMenu(subid.trim());
                    } else if ("SubscriberInvalid".equals(response)) {
                        // Invalid SUBID - show error
                        statusLabel.setText("");
                        showError("Invalid ID. Please check your ID and try again.");
                        loginButton.setDisable(false);
                        continueAsGuestButton.setDisable(false);
                    } else {
                        // Error or unknown response
                        statusLabel.setText("");
                        showError("Error validating ID. Please try again.");
                        loginButton.setDisable(false);
                        continueAsGuestButton.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("");
                    showError("Error connecting to server. Please try again.");
                    loginButton.setDisable(false);
                    continueAsGuestButton.setDisable(false);
                    e.printStackTrace();
                });
            }
        }).start();
    }

    /**
     * Handles the "Continue as Guest" button click.
     * Navigates directly to Guest Menu without validation.
     */
    @FXML
    private void handleContinueAsGuest() {
        UserSessionHelper.setGuest();
        navigateToGuestMenu();
    }

    /**
     * Navigates to the Subscriber Menu screen.
     * 
     * @param subid The validated subscriber ID
     */
    private void navigateToSubscriberMenu(String subid) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/SubMenu.fxml"));
            Parent root = loader.load();
            SubMenuController controller = loader.getController();
            controller.setSubscriberID(subid);

            Stage stage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/gui/SubMenu.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Subscriber Menu");

            // Handle window close
            stage.setOnCloseRequest(closeEvent -> {
                try {
                    if (ClientUI.chat != null) {
                        HashMap<String, String> disconnectMsg = new HashMap<>();
                        disconnectMsg.put("Disconnect", "");
                        ClientUI.chat.accept(disconnectMsg);
                        Thread.sleep(200);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            stage.show();
            closeCurrentWindow();
        } catch (IOException e) {
            showError("Failed to load Subscriber Menu.");
            e.printStackTrace();
        }
    }

    /**
     * Navigates to the Guest Menu screen.
     */
    private void navigateToGuestMenu() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/GuestMenu.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/gui/GuestMenu.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Guest Menu");

            // Handle window close
            stage.setOnCloseRequest(closeEvent -> {
                try {
                    if (ClientUI.chat != null) {
                        HashMap<String, String> disconnectMsg = new HashMap<>();
                        disconnectMsg.put("Disconnect", "");
                        ClientUI.chat.accept(disconnectMsg);
                        Thread.sleep(200);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            stage.show();
            closeCurrentWindow();
        } catch (IOException e) {
            showError("Failed to load Guest Menu.");
            e.printStackTrace();
        }
    }

    /**
     * Closes the current window.
     */
    private void closeCurrentWindow() {
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Shows an error alert dialog.
     * 
     * @param message The error message to display
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

