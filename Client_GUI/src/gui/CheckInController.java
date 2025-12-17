package gui;

import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class CheckInController {

    @FXML
    private TextField confirmationCodeField;

    @FXML
    private Label statusLabel;

    // פעולה לביצוע CheckIn
    @FXML
    private void handleCheckIn() {
        String code = confirmationCodeField.getText();

        if (code == null || code.isEmpty()) {
            showError("Please enter your confirmation code.");
            return;
        }

        statusLabel.setText("Checking in... Please wait");

        HashMap<String, String> request = new HashMap<>();
        request.put("CheckIn", code);

        // ❗ חוסם עד לקבלת תשובה מהשרת
        ClientUI.chat.accept(request);

        if ("CheckInSuccess".equals(ChatClient.fromserverString)) {
            statusLabel.setText("");
            showInfo("Check-In successful!");
        } 
        else if ("CheckInFailed".equals(ChatClient.fromserverString)) {
            statusLabel.setText("");
            showError("Invalid confirmation code");
        } 
        else {
            statusLabel.setText("");
            showError("Server error");
        }

        ChatClient.ResetServerString();
    }



    @FXML
    private void handleLostCode() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/gui/LostCode.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Recover Confirmation Code");
            stage.setScene(new Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to open Lost Code screen");
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
