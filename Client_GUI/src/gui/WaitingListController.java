package gui;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.AlertHelper;
import common.UserSessionHelper;
import entities.WaitingEntry;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controller for the Waiting List screen.
 * Allows users to join the waiting list when the restaurant is full.
 * For subscribers, automatically uses their stored information (phone, email).
 */
public class WaitingListController {

    @FXML
    private TextField guestsField;

    @FXML
    private TextField phoneField;

    @FXML
    private TextField emailField;
    
    @FXML
    private Label lblPhone;
    @FXML
    private Label lblEmail;

    @FXML
    private Label lblStatus;

    @FXML
    private Button btnBack;

    @FXML
    public void initialize() {
    	// For subscribers, hide phone/email fields and labels (data comes from DB)
    	if (UserSessionHelper.isSubscriber()) {
    		if (phoneField != null) {
    			phoneField.setVisible(false);
    			phoneField.setManaged(false);
    		}
    		if (emailField != null) {
    			emailField.setVisible(false);
    			emailField.setManaged(false);
    		}
    		if (lblPhone != null) {
    			lblPhone.setVisible(false);
    			lblPhone.setManaged(false);
    		}
    		if (lblEmail != null) {
    			lblEmail.setVisible(false);
    			lblEmail.setManaged(false);
    		}
    	}
    }
    
    /**
     * Handles the Submit button click.
     * Validates input and sends waiting list entry request to the server.
     * For guests, requires phone and email input. For subscribers, uses stored information.
     */
    @FXML
    private void handleSubmit() {
        String guestsText = guestsField.getText();
        String phone = phoneField.getText();
        String email = emailField.getText();

        boolean isSubscriber = UserSessionHelper.isSubscriber();

        if (guestsText.isEmpty()) {
            lblStatus.setText("Please enter number of guests.");
            return;
        }

        int numberOfGuests;
        try {
            numberOfGuests = Integer.parseInt(guestsText);
            if (numberOfGuests <= 0) {
                lblStatus.setText("Number of guests must be positive.");
                return;
            }
        } catch (NumberFormatException e) {
            lblStatus.setText("Number of guests must be a number.");
            return;
        }

        if (!isSubscriber) {
	        // === Guest: validate phone/email from user input ===
	        if (phone.isEmpty() || email.isEmpty()) {
	            lblStatus.setText("Please fill all fields.");
	            return;
	        }
	        
	        if (!phone.matches("\\d{10,12}")) {
	            lblStatus.setText("Invalid phone number format.");
	            return;
	        }
	
	        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
	            lblStatus.setText("Invalid email format.");
	            return;
	        }
	
	        LocalDate date = LocalDate.now();
	        WaitingEntry entry = new WaitingEntry(numberOfGuests, phone, email, date);
	
	        lblStatus.setText("Sending to server...\nPlease wait...");
	
	        new Thread(() -> {
	            // Reset response string before sending request
	            ChatClient.ResetServerString();
	            
	            // Send request to server
	            ClientUI.chat.accept(entry);
	            
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
	                
	                if (response.startsWith("TABLE_AVAILABLE:")) {
	                    // Restaurant has available space - show table number
	                    String tableLocation = response.substring("TABLE_AVAILABLE:".length());
	                    lblStatus.setText("");
	                    AlertHelper.showInfo("Table Available", "Great news! A table is available right now.\n\nYour table: " + tableLocation + "\n\nYou can proceed to your table.");
	                    guestsField.clear();
	                    phoneField.clear();
	                    emailField.clear();
                } else if (response.startsWith("WAITING_LIST:")) {
                    // Restaurant is full - show confirmation code
                    String confirmationCode = response.substring("WAITING_LIST:".length());
                    lblStatus.setText("");
                    AlertHelper.showInfo("Waiting List", "The restaurant is currently full.\n\nYou have been added to the waiting list.\n\nYour confirmation code: " + confirmationCode + "\n\nYou will receive an SMS when a table becomes available.\n\nPlease use this code when a table becomes available.");
                    guestsField.clear();
                    phoneField.clear();
                    emailField.clear();
	                } else if (response.isEmpty()) {
	                    lblStatus.setText("No response from server. Please try again.");
	                } else {
	                    lblStatus.setText("Failed to add to waiting list. Server response: " + response);
	                }
	            });
	
	        }).start();
        } else {
        	String subscriberId = UserSessionHelper.getSubscriberID();
        	if (subscriberId == null || subscriberId.isEmpty()) {
        		lblStatus.setText("Subscriber ID missing. Please re-login.");
        		return;
        	}
        	
        	HashMap<String, String> req = new HashMap<>();
        	String value = subscriberId + " " + numberOfGuests;
        	req.put("JoinWaitingListSub", value);
        	
        	lblStatus.setText("Sending to server...\nPlease wait...");
        	
        	new Thread(() -> {
        		ChatClient.ResetServerString();
        		ClientUI.chat.accept(req);
        		
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
        			
        			if (response.startsWith("TABLE_AVAILABLE:")) {
        				// Restaurant has available space - show table number
        				String tableLocation = response.substring("TABLE_AVAILABLE:".length());
        				lblStatus.setText("");
        				AlertHelper.showInfo("Table Available", "Great news! A table is available right now.\n\nYour table: " + tableLocation + "\n\nYou can proceed to your table.");
        				guestsField.clear();
        			} else if (response.startsWith("WAITING_LIST:")) {
        				// Restaurant is full - show confirmation code
        				String confirmationCode = response.substring("WAITING_LIST:".length());
        				lblStatus.setText("");
        				AlertHelper.showInfo("Waiting List", "The restaurant is currently full.\n\nYou have been added to the waiting list.\n\nYour confirmation code: " + confirmationCode + "\n\nYou will receive an SMS when a table becomes available.\n\nPlease use this code when a table becomes available.");
        				guestsField.clear();
        			} else if (response.isEmpty()) {
        				lblStatus.setText("No response from server. Please try again.");
        			} else {
        				lblStatus.setText("Failed to add to waiting list. Server response: " + response);
        			}
        		});
        	}).start();
        }
    }

    /**
     * Handles the Back button click.
     * Closes the current screen and navigates back to the appropriate menu.
     * 
     * @param event The click event on the back button
     * @throws IOException If navigation fails
     */
    public void Back(ActionEvent event) throws IOException {
        // Use centralized navigation that handles context restoration
        UserSessionHelper.navigateBackToMenu((Node) event.getSource());
    }

    // Alert methods removed - now using AlertHelper static methods
}

