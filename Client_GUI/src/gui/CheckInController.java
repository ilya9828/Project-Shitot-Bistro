package gui;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class CheckInController {

    @FXML
    private TextField confirmationCodeField;

    @FXML
    private Label statusLabel;

    // פעולה לביצוע CheckIn
    @FXML
    private void handleCheckIn() {
        String code = confirmationCodeField.getText();

        if (code.isEmpty()) {
            showError("Please enter your confirmation code.");
            return;
        }

        // כאן אפשר להוסיף לוגיקה לשליחת הקוד לשרת או בדיקה מול DB
        statusLabel.setText("Checked in with code: " + code);
        showInfo("Check-In successful!");
    }

    // פעולה במקרה שהקוד אבד
    @FXML
    private void handleLostCode() {
        String code = confirmationCodeField.getText();

        if (code.isEmpty()) {
            showError("No confirmation code entered. We'll generate a new one.");
        }

        // כאן אפשר להוסיף לוגיקה לשליחת קוד חדש או בקשת קוד מחדש
        statusLabel.setText("Lost code requested");
        showInfo("A new confirmation code has been sent to you.");
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
