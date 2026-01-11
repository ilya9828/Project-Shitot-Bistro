package gui;

import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class CheckInController {

    @FXML
    private TextField confirmationCodeField;

    @FXML
    private Label statusLabel;
    
    @FXML
    private Button backButton;

    // FIX: Check-In operation with proper async handling
    @FXML
    private void handleCheckIn() {
        String code = confirmationCodeField.getText().trim();

        if (code == null || code.isEmpty()) {
            showError("Please enter your confirmation code.");
            return;
        }

        statusLabel.setText("Checking in... Please wait");

        HashMap<String, String> request = new HashMap<>();
        request.put("CheckIn", code);

        // FIX: Use thread for async communication with proper response waiting
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
                    showInfo(msg);
                } 
                else if (response.equals("CheckInFailed") || response.startsWith("CheckInFailed:")) {
                    statusLabel.setText("");
                    String errorMsg = "Check-in failed.\n\n";
                    if (response.startsWith("CheckInFailed:Reservation status is not PENDING")) {
                        errorMsg += "This reservation cannot be checked in because its status is not PENDING.\n\nOnly reservations with status 'PENDING' can be checked in.";
                    } else {
                        errorMsg += "Invalid confirmation code or server error.\n\nDetails: " + response;
                    }
                    showError(errorMsg);
                }
                else if (response.equals("NoTableAvailable")) {
                    statusLabel.setText("");
                    showError("There is currently no available table for your party.\n"
                             + "Please wait at the entrance, we will notify you as soon as a table is free.");
                }
                else if (response.isEmpty()) {
                    statusLabel.setText("");
                    showError("No response from server. Please try again.");
                }
                else {
                    statusLabel.setText("");
                    showError("Server error: " + response);
                }
            });
        }).start();
    }



    @FXML
    private void handleLostCode() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/gui/LostCode.fxml")
            );
            javafx.scene.Parent root = loader.load();

            Stage stage = new Stage();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            // FIX: Add CSS stylesheet
            scene.getStylesheets().add(getClass().getResource("/gui/LostCode.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Recover Confirmation Code");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to open Lost Code screen: " + e.getMessage());
        }
    }
    
    /**
     * Back button – return to appropriate menu (Guest or Subscriber).
     */
    @FXML
    private void handleBackToMenu() {
        try {
            // Navigate back to appropriate menu based on user type
            boolean isGuest = UserSessionHelper.isGuest();
            String menuFile = isGuest ? "/gui/GuestMenu.fxml" : "/gui/SubMenu.fxml";
            String cssFile = isGuest ? "/gui/GuestMenu.css" : "/gui/SubMenu.css";
            String title = isGuest ? "Guest Menu" : "Subscriber Menu";
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource(menuFile));
            javafx.scene.Parent root = loader.load();

            // If subscriber, set the subscriber ID
            if (!isGuest) {
                try {
                    Object controller = loader.getController();
                    if (controller instanceof SubMenuController) {
                        ((SubMenuController) controller).setSubscriberID(UserSessionHelper.getSubscriberID());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Stage stage = new Stage();
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            scene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());
            stage.setScene(scene);
            stage.setTitle(title);
            stage.show();

            // close current CheckIn window
            Stage current = (Stage) backButton.getScene().getWindow();
            current.close();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to go back to main menu: " + e.getMessage());
        }
    }


    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
    
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
