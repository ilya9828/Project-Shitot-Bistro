package gui;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import entities.WaitingEntry;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;


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
	                    showInfo("Great news! A table is available right now.\n\nYour table: " + tableLocation + "\n\nYou can proceed to your table.");
	                    guestsField.clear();
	                    phoneField.clear();
	                    emailField.clear();
                } else if (response.startsWith("WAITING_LIST:")) {
                    // Restaurant is full - show confirmation code
                    String confirmationCode = response.substring("WAITING_LIST:".length());
                    lblStatus.setText("");
                    showInfo("The restaurant is currently full.\n\nYou have been added to the waiting list.\n\nYour confirmation code: " + confirmationCode + "\n\nYou will receive an SMS when a table becomes available.\n\nPlease use this code when a table becomes available.");
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
        				showInfo("Great news! A table is available right now.\n\nYour table: " + tableLocation + "\n\nYou can proceed to your table.");
        				guestsField.clear();
        			} else if (response.startsWith("WAITING_LIST:")) {
        				// Restaurant is full - show confirmation code
        				String confirmationCode = response.substring("WAITING_LIST:".length());
        				lblStatus.setText("");
        				showInfo("The restaurant is currently full.\n\nYou have been added to the waiting list.\n\nYour confirmation code: " + confirmationCode + "\n\nYou will receive an SMS when a table becomes available.\n\nPlease use this code when a table becomes available.");
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


    public void Back(ActionEvent event) throws IOException {
    	// Navigate back to appropriate menu (Guest or Subscriber)
    	boolean isGuest = UserSessionHelper.isGuest();
    	String fxml = isGuest ? "/gui/GuestMenu.fxml" : "/gui/SubMenu.fxml";
    	String css = isGuest ? "/gui/GuestMenu.css" : "/gui/SubMenu.css";
    	String title = isGuest ? "Guest Menu" : "Subscriber Menu";
    	
        FXMLLoader menuLoader = new FXMLLoader(getClass().getResource(fxml));
        Parent menuRoot = menuLoader.load();
        
        // If subscriber, pass subscriberID to SubMenuController
        if (!isGuest) {
        	try {
        		Object controller = menuLoader.getController();
        		if (controller instanceof SubMenuController) {
        			((SubMenuController) controller).setSubscriberID(UserSessionHelper.getSubscriberID());
        		}
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        
        Stage menuStage = new Stage();
        Scene menuScene = new Scene(menuRoot);
        menuScene.getStylesheets().add(getClass().getResource(css).toExternalForm());
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
        ((Node) event.getSource()).getScene().getWindow().hide();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}

