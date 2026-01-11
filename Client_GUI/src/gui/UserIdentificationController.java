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
     * Validates the ID with the server and navigates to the appropriate menu (Subscriber, Staff, or Manager).
     */
    @FXML
    private void handleLoginAsSubscriber() {
        String userId = subidField.getText();

        // Validate that ID is not empty
        if (userId == null || userId.trim().isEmpty()) {
            showError("Please enter ID.");
            return;
        }

        // Disable buttons during validation
        loginButton.setDisable(true);
        continueAsGuestButton.setDisable(true);
        statusLabel.setText("Validating ID... Please wait...");

        // Send user ID to server for validation
        new Thread(() -> {
            try {
                HashMap<String, String> loginRequest = new HashMap<>();
                loginRequest.put("ValidateUserID", userId.trim());
                
                ClientUI.chat.accept(loginRequest);

                // Wait for server response
                Thread.sleep(500);

                Platform.runLater(() -> {
                    String response = ChatClient.fromserverString;
                    ChatClient.ResetServerString();

                    statusLabel.setText("");
                    
                    if ("Subscriber".equals(response)) {
                        // Login successful - navigate to Subscriber Menu
                        UserSessionHelper.setSubscriber(userId.trim());
                        navigateToSubscriberMenu(userId.trim());
                    } else if ("Staff".equals(response)) {
                        // Login successful - navigate to Staff Menu
                        UserSessionHelper.setStaff(userId.trim());
                        navigateToStaffMenu(userId.trim());
                    } else if ("Manager".equals(response)) {
                        // Login successful - navigate to Manager Menu
                        UserSessionHelper.setManager(userId.trim());
                        navigateToManagerMenu(userId.trim());
                    } else {
                        // Invalid ID or error
                        showError("Invalid ID. Please check your ID and try again.");
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
     * Navigates to the Staff Menu screen.
     * 
     * @param staffId The validated staff ID
     */
    private void navigateToStaffMenu(String staffId) {
        try {
            java.net.URL fxmlUrl = getClass().getResource("/gui/StaffMenu.fxml");
            if (fxmlUrl == null) {
                throw new IOException("StaffMenu.fxml not found. Please create the Staff Menu screen.");
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Stage stage = new Stage();
            Scene scene = new Scene(root);
            java.net.URL cssUrl = getClass().getResource("/gui/StaffMenu.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            stage.setScene(scene);
            stage.setTitle("Staff Menu");

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
        } catch (IOException | IllegalStateException e) {
            showError("Staff Menu screen not found. Please create StaffMenu.fxml and StaffMenu.css files.");
            e.printStackTrace();
            loginButton.setDisable(false);
            continueAsGuestButton.setDisable(false);
        }
    }

    /**
     * Navigates to the Manager Menu screen.
     * 
     * @param managerId The validated manager ID
     */
    private void navigateToManagerMenu(String managerId) {
        try {
            java.net.URL fxmlUrl = getClass().getResource("/gui/ManagerMenu.fxml");
            if (fxmlUrl == null) {
                throw new IOException("ManagerMenu.fxml not found. Please create the Manager Menu screen.");
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Stage stage = new Stage();
            Scene scene = new Scene(root);
            java.net.URL cssUrl = getClass().getResource("/gui/ManagerMenu.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            stage.setScene(scene);
            stage.setTitle("Manager Menu");

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
        } catch (IOException | IllegalStateException e) {
            showError("Manager Menu screen not found. Please create ManagerMenu.fxml and ManagerMenu.css files.");
            e.printStackTrace();
            loginButton.setDisable(false);
            continueAsGuestButton.setDisable(false);
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

