package gui;

import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class LostCodeController {

    @FXML
    private TextField inputField;

    @FXML
    private Label resultLabel;

    @FXML
    private void handleRecover() {
        String input = inputField.getText();

        if (input == null || input.isEmpty()) {
            showError("Please enter email or phone number");
            return;
        }

        if (!input.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$") &&
            !input.matches("\\d{10,12}")) {
            showError("Invalid email or phone number");
            return;
        }

        HashMap<String, String> request = new HashMap<>();
        request.put("LostCode", input);

        // ❗ הקריאה הזו חוסמת עד שהשרת עונה
        ClientUI.chat.accept(request);

        // עכשיו בוודאות יש תשובה
        String response = ChatClient.fromserverString;

        if ("LostOrderFailed".equals(response)) {
            showError("Order not found");
        }
        else if (response != null && response.startsWith("OrderCode:")) {
            String recoveredCode = response.substring("OrderCode:".length());
            resultLabel.setText(
                "Your confirmation code is:\n" + recoveredCode
            );
        }
        else {
            showError("Server error");
        }

        ChatClient.ResetServerString();
    }


    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
