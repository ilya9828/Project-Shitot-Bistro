package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import entities.Payment;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller for the Pay Bill screen.
 * Handles payment simulation (academic purposes only - no real payment processing).
 */
public class PayBillController {

    @FXML
    private TextField confirmationCodeField;

    @FXML
    private Label totalBillLabel;

    @FXML
    private Label discountLabel;

    @FXML
    private Button payButton;

    @FXML
    private Button btnCancel;

    @FXML
    private Label statusLabel;

    @FXML
    private VBox cardDetailsContainer;

    @FXML
    private TextField cardNumberField;

    @FXML
    private TextField cardHolderNameField;

    @FXML
    private TextField expiryDateField;

    @FXML
    private TextField cvvField;

    // Simulated values (in a real application, these would come from the server)
    private static final double SIMULATED_BILL_AMOUNT = 250.00;
    private static final double MEMBER_DISCOUNT_PERCENTAGE = 10.0;
    private boolean isMember = false; // This would be determined from server/database

    /**
     * Initializes the controller.
     * Sets up the payment form and displays initial bill information.
     */
    @FXML
    public void initialize() {
        // Check if user is subscriber (member)
        isMember = UserSessionHelper.isSubscriber();

        // Card details are always visible (Credit Card is the only payment method)
        cardDetailsContainer.setVisible(true);

        // Display initial bill amount
        updateBillDisplay();
    }

    /**
     * Updates the bill display with total amount and discount information.
     */
    private void updateBillDisplay() {
        double total = SIMULATED_BILL_AMOUNT;
        
        if (isMember) {
            double discount = total * (MEMBER_DISCOUNT_PERCENTAGE / 100.0);
            double finalAmount = total - discount;
            totalBillLabel.setText(String.format("₪%.2f", finalAmount));
            discountLabel.setText(String.format("₪%.2f (10%%)", discount));
        } else {
            totalBillLabel.setText(String.format("₪%.2f", total));
            discountLabel.setText("Not applicable");
        }
    }

    /**
     * Handles the Pay button click event.
     * Validates the confirmation code and sends payment to server.
     */
    @FXML
    private void handlePay() {
        // Validate confirmation code
        String confirmationCode = confirmationCodeField.getText();
        
        if (confirmationCode == null || confirmationCode.trim().isEmpty()) {
            showError("Please enter a confirmation code.");
            return;
        }

        // Payment method is always Credit Card
        String paymentMethod = "Credit Card";

        // Validate card details
        if (cardNumberField.getText() == null || cardNumberField.getText().trim().isEmpty()) {
            showError("Please enter card number.");
            return;
        }
        if (cardHolderNameField.getText() == null || cardHolderNameField.getText().trim().isEmpty()) {
            showError("Please enter cardholder name.");
            return;
        }
        if (expiryDateField.getText() == null || expiryDateField.getText().trim().isEmpty()) {
            showError("Please enter expiry date (MM/YY).");
            return;
        }
        if (cvvField.getText() == null || cvvField.getText().trim().isEmpty()) {
            showError("Please enter CVV.");
            return;
        }

        // Calculate amount
        double total = SIMULATED_BILL_AMOUNT;
        double discount = 0.0;
        if (isMember) {
            discount = total * (MEMBER_DISCOUNT_PERCENTAGE / 100.0);
            total = total - discount;
        }

        // Create Payment object (always Credit Card)
        Payment payment = new Payment(
            confirmationCode,
            paymentMethod,
            total,
            isMember,
            discount,
            cardNumberField.getText().trim(),
            cardHolderNameField.getText().trim(),
            expiryDateField.getText().trim(),
            cvvField.getText().trim()
        );

        // Send payment to server
        statusLabel.setText("Processing payment...");
        payButton.setDisable(true);

        new Thread(() -> {
            try {
                // Reset response string before sending request
                ChatClient.ResetServerString();
                
                // Send payment to server
                ClientUI.chat.accept(payment);

                // Wait for server response (wait until string is no longer empty)
                while (ChatClient.fromserverString.equals(new String())) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }

                Platform.runLater(() -> {
                    String response = ChatClient.fromserverString;
                    ChatClient.ResetServerString();

                    if ("PaymentSuccess".equals(response)) {
                        showSuccess("Payment successful!\n" +
                                   "Confirmation Code: " + confirmationCode + "\n" +
                                   "Payment Method: " + paymentMethod + "\n" +
                                   "Amount: " + totalBillLabel.getText());
                        
                        // Close the Pay Bill screen
                        closeWindow();
                    } else if (response != null && response.startsWith("PaymentFailed")) {
                        statusLabel.setText("Payment failed. Please try again.");
                        payButton.setDisable(false);
                        String errorMsg = response.contains(":") ? response.substring(response.indexOf(":") + 1).trim() : "Payment processing failed. Please try again.";
                        showError(errorMsg);
                    } else if (response.isEmpty()) {
                        statusLabel.setText("No response from server. Please try again.");
                        payButton.setDisable(false);
                        showError("No response from server. Please try again.");
                    } else {
                        statusLabel.setText("Error: " + response);
                        payButton.setDisable(false);
                        showError("Server error: " + response);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error communicating with server.");
                    payButton.setDisable(false);
                    showError("Error communicating with server: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }


    /**
     * Handles the Cancel button click event.
     * Closes the Pay Bill screen and returns to the menu.
     */
    @FXML
    private void handleCancel() {
        closeWindow();
    }

    /**
     * Closes the current window and returns to the appropriate menu.
     */
    private void closeWindow() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
        
        // Navigate back to appropriate menu based on user type
        try {
            boolean isSubscriber = UserSessionHelper.isSubscriber();
            String fxmlFile = isSubscriber ? "/gui/SubMenu.fxml" : "/gui/GuestMenu.fxml";
            String cssFile = isSubscriber ? "/gui/SubMenu.css" : "/gui/GuestMenu.css";
            String title = isSubscriber ? "Subscriber Menu" : "Guest Menu";
            
            FXMLLoader menuLoader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent menuRoot = menuLoader.load();
            Stage menuStage = new Stage();
            Scene menuScene = new Scene(menuRoot);
            menuScene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());
            menuStage.setScene(menuScene);
            menuStage.setTitle(title);
            
            menuStage.setOnCloseRequest(closeEvent -> {
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
            
            menuStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the Exit button click event.
     * Disconnects from the server and exits the application.
     */

    /**
     * Shows a success alert dialog.
     * 
     * @param message The success message to display
     */
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Payment Success");
        alert.setHeaderText("Payment Completed Successfully");
        alert.setContentText(message);
        alert.showAndWait();
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

