package gui;

import java.io.IOException;

import client.ChatClient;
import client.ClientUI;
import common.AlertHelper;
import common.UserSessionHelper;
import entities.Payment;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

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
            AlertHelper.showError("Error", "Please enter a confirmation code.");
            return;
        }

        // Payment method is always Credit Card
        String paymentMethod = "Credit Card";

        // Validate card details
        if (cardNumberField.getText() == null || cardNumberField.getText().trim().isEmpty()) {
            AlertHelper.showError("Error", "Please enter card number.");
            return;
        }
        if (cardHolderNameField.getText() == null || cardHolderNameField.getText().trim().isEmpty()) {
            AlertHelper.showError("Error", "Please enter cardholder name.");
            return;
        }
        if (expiryDateField.getText() == null || expiryDateField.getText().trim().isEmpty()) {
            AlertHelper.showError("Error", "Please enter expiry date (MM/YY).");
            return;
        }
        if (cvvField.getText() == null || cvvField.getText().trim().isEmpty()) {
            AlertHelper.showError("Error", "Please enter CVV.");
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
                        AlertHelper.showSuccess("Payment Success", "Payment successful!\n" +
                                   "Confirmation Code: " + confirmationCode + "\n" +
                                   "Payment Method: " + paymentMethod + "\n" +
                                   "Amount: " + totalBillLabel.getText());
                        
                        // Close the Pay Bill screen
                        closeWindow();
                    } else if (response != null && response.startsWith("PaymentFailed")) {
                        statusLabel.setText("Payment failed. Please try again.");
                        payButton.setDisable(false);
                        String errorMsg = response.contains(":") ? response.substring(response.indexOf(":") + 1).trim() : "Payment processing failed. Please try again.";
                        AlertHelper.showError("Payment Error", errorMsg);
                    } else if (response.isEmpty()) {
                        statusLabel.setText("No response from server. Please try again.");
                        payButton.setDisable(false);
                        AlertHelper.showError("Error", "No response from server. Please try again.");
                    } else {
                        statusLabel.setText("Error: " + response);
                        payButton.setDisable(false);
                        AlertHelper.showError("Server Error", "Server error: " + response);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error communicating with server.");
                    payButton.setDisable(false);
                    AlertHelper.showError("Error", "Error communicating with server: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        }).start();
    }


    /**
     * Handles the Back button click event.
     * Returns to the menu.
     */
    @FXML
    private void handleCancel() {
        closeWindow();
    }

    /**
     * Closes the current window and returns to the appropriate menu.
     */
    private void closeWindow() {
        try {
            // Use centralized navigation that handles context restoration
            // navigateBackToMenu will replace the scene in the current window, so we don't need to close it
            UserSessionHelper.navigateBackToMenu(btnCancel);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

