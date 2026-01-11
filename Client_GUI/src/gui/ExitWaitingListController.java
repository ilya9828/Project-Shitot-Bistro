package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
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

/**
 * Controller for the Exit Waiting List screen.
 * Allows users to remove themselves from the waiting list using phone or email.
 */
public class ExitWaitingListController {

    @FXML
    private TextField phoneOrEmailField;

    @FXML
    private Label statusLabel;
    
    @FXML
    private Label lblPhoneOrEmail;
    
    @FXML
    private Label lblInstructions;

    @FXML
    private Button btnExit;

    @FXML
    private Button btnBack;

    @FXML
    public void initialize() {
    	// For subscribers, hide manual input – we will use subscriber info from DB
    	if (UserSessionHelper.isSubscriber()) {
    		if (phoneOrEmailField != null) {
    			phoneOrEmailField.setVisible(false);
    			phoneOrEmailField.setManaged(false);
    		}
    		if (lblPhoneOrEmail != null) {
    			lblPhoneOrEmail.setVisible(false);
    			lblPhoneOrEmail.setManaged(false);
    		}
    		if (lblInstructions != null) {
    			lblInstructions.setVisible(false);
    			lblInstructions.setManaged(false);
    		}
    	}
    }
    
    /**
     * Handles the Exit button click.
     * Sends request to server to remove user from waiting list.
     */
    @FXML
    private void handleExit() {
    	boolean isSubscriber = UserSessionHelper.isSubscriber();
    	
    	String phoneOrEmail;
    	
    	if (!isSubscriber) {
	        phoneOrEmail = phoneOrEmailField.getText().trim();
	
	        if (phoneOrEmail.isEmpty()) {
	            showError("Please enter your phone number or email address.");
	            return;
	        }
	
	        // Basic validation
	        if (!phoneOrEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$") && 
	            !phoneOrEmail.matches("\\d{10,12}")) {
	            showError("Please enter a valid phone number or email address.");
	            return;
	        }
    	} else {
    		// For subscribers, we only send subscriberID; server finds phone/email
    		phoneOrEmail = UserSessionHelper.getSubscriberID();
    		if (phoneOrEmail == null || phoneOrEmail.isEmpty()) {
    			showError("Subscriber ID missing. Please re-login.");
    			return;
    		}
    	}

        statusLabel.setText("Processing request... Please wait");

        HashMap<String, String> request = new HashMap<>();
        if (isSubscriber) {
        	request.put("ExitWaitingListSub", phoneOrEmail);
        } else {
        	request.put("ExitWaitingList", phoneOrEmail);
        }

        // Use thread for async communication
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
                
                if (response != null && response.startsWith("WaitingListExited")) {
                    statusLabel.setText("");
                    if (!isSubscriber) {
                    	phoneOrEmailField.clear();
                    }
                    showInfo("Successfully removed from waiting list!");
                } else {
                    statusLabel.setText("");
                    String errorMsg = "Failed to exit waiting list.";
                    if (response != null && response.contains(":")) {
                        errorMsg = response.substring(response.indexOf(":") + 1).trim();
                    }
                    showError(errorMsg);
                }
            });
        }).start();
    }

    /**
     * Handles the Back button click.
     * Navigates back to the appropriate menu (Guest or Subscriber).
     */
    @FXML
    public void Back(ActionEvent event) throws IOException {
        // Navigate back to appropriate menu based on user type
        boolean isGuest = UserSessionHelper.isGuest();
        String menuFile = isGuest ? "/gui/GuestMenu.fxml" : "/gui/SubMenu.fxml";
        String cssFile = isGuest ? "/gui/GuestMenu.css" : "/gui/SubMenu.css";
        String title = isGuest ? "Guest Menu" : "Subscriber Menu";
        
        FXMLLoader menuLoader = new FXMLLoader(getClass().getResource(menuFile));
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
        ((Node) event.getSource()).getScene().getWindow().hide();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
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

