package gui;

import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LostCodeController {

    @FXML
    private TextField inputField;

    @FXML
    private Label resultLabel;
    
    @FXML
    private Button backButton;

    @FXML
    private void handleRecover() {
        String input = inputField.getText();

        if (input == null || input.isEmpty()) {
            showError("Please enter the email address or phone number you used when making your reservation.");
            return;
        }

        // Trim and validate input
        input = input.trim();
        
        if (!input.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$") &&
            !input.matches("\\d{10,12}")) {
            showError("Invalid format.\n\nPlease enter:\n• Email: example@email.com\n• Phone: 05XXXXXXXX (10-12 digits)");
            return;
        }

        HashMap<String, String> request = new HashMap<>();
        request.put("LostCode", input);

        // FIX: Use async communication with proper response waiting
        new Thread(() -> {
            // Reset response string before sending request
            ChatClient.ResetServerString();
            
            // Send request to server
            ClientUI.chat.accept(request);
            
            // Wait for server response (wait until string is no longer empty)
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
                
                if ("LostOrderFailed".equals(response)) {
                    showError("No reservation found for this email or phone number.\n\nPlease make sure:\n• You entered the same email or phone number you used when making the reservation\n• The reservation is for today\n• The reservation status is 'PENDING'");
                }
                else if ("CodeSent".equals(response)) {
                    resultLabel.setText("");
                    showInfo("Your confirmation code has been sent to your email and phone number.\n\nPlease check your messages.");
                    inputField.clear();
                }
                else if (response.isEmpty()) {
                    showError("No response from server. Please try again.");
                }
                else {
                    showError("Server error: " + response);
                }
            });
        }).start();
    }

    /**
     * Back button – close LostCode window and return to the existing CheckIn screen.
     * (CheckIn stays open behind, so here we only close the current screen)
     */
    @FXML
    private void handleBack() {
        Stage current = (Stage) backButton.getScene().getWindow();
        current.close();
    }


    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
    
    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
