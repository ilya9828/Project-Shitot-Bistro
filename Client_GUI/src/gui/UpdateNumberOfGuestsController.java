package gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import client.ChatClient;
import client.ClientUI;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * This class is showing the GUI of updating phone number of subscriber.
 */
public class UpdateNumberOfGuestsController {

	@FXML
	private Label lblinstruction;
	@FXML 
	private Label lblOrderNumber;
	@FXML 
	private Label lblOrderDate;
	@FXML 
	private Label lblGuests;
	@FXML 
	private Label lblConfirm;
	@FXML 
	private Label lblSubscriberId;
	@FXML 
	private Label lblPlacingDate;
	@FXML
	private Label lblStatus;
	@FXML
	private Label lblErr;

	@FXML 
	private TextField txtOrderNumber;
	@FXML 
	private TextField txtOrderDate;
	@FXML 
	private TextField txtGuests;
	@FXML 
	private TextField txtConfirm;
	@FXML 
	private TextField txtSubscriberId;
	@FXML 
	private TextField txtPlacingDate;


	@FXML
	private Button btnLoad = null;
	@FXML
	private Button btnUpdate = null;
	@FXML
	private Button btnExit = null;
	@FXML
	private Button btnBack = null;

	private int loaded = 0;
	
    /** This method is for the back button closing the current GUI and uploading the menu GUI.
     * @param event - click on the back button.
     * @throws IOException
     */
	public void Back(ActionEvent event) throws IOException {
		FXMLLoader studentLoader = new FXMLLoader(getClass().getResource("/gui/Menu.fxml"));
		Parent studentRoot = studentLoader.load();
		Stage studentStage = new Stage();
		Scene studentScene = new Scene(studentRoot);
		studentScene.getStylesheets().add(getClass().getResource("/gui/Menu.css").toExternalForm());
		studentStage.setScene(studentScene);
		studentStage.setTitle("Menu");
		studentStage.show();
		((Node) event.getSource()).getScene().getWindow().hide();
	}

	/**
	 * This method is for the update button is sending the information to the server
	 * which subscriber to update and what to change.
	 * 
	 * @param event - the click on the update button.
	 */
	public void UpdateBtn(ActionEvent event) {

	    if (loaded <= 0) {
	        lblErr.setText("You must load an OrderNumber first.");
	        return;
	    }

	    // Build update data
	    HashMap<String, String> updateHashMap = new HashMap<>();
	    String newInfo = txtOrderNumber.getText() + " " + txtGuests.getText();
	    updateHashMap.put("UpdateNumberOfGuests", newInfo);

	    lblStatus.setText("Request sent to the server.\nPlease wait...");

	    // Run on a background thread
	    new Thread(() -> {
	        ClientUI.chat.accept(updateHashMap);

	        // After server response, update UI on JavaFX thread
	        Platform.runLater(() -> {
	            if ("Updated".equals(ChatClient.fromserverString)) {
	                lblStatus.setText("Updated successfully.");
	            } else {
	                lblStatus.setText("Can't update.");
	            }
	            ChatClient.ResetServerString();
	        });
	    }).start();
	}


	/**
	 * This method is for the load subscriber button. getting the string from the
	 * server and calling other method "LoadDetails()" to handle it and load into the GUI
	 * 
	 * @param event - the click on the load button.
	 */
	public void Loadbtn(ActionEvent event) {
		String idnumber = txtOrderNumber.getText();
		//The string contains only numbers.
		if (idnumber.matches("\\d+")) {
		    System.out.println("");
		    HashMap<String, String> loadthisid = new HashMap<String, String>();
			loadthisid.put("LoadOrders", idnumber);
			ClientUI.chat.accept(loadthisid);
			while (ChatClient.fromserverString.equals(new String())) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (!ChatClient.fromserverString.contains(",")) {
				lblErr.setText("Cant load the requested ID. Please make sure you entered the right id.");
			} else {
				lblErr.setText("Requested ID loaded.\nYou can update ONLY the phone number:");
				loaded = 1;
				LoadDetails(ChatClient.fromserverString);
			}
			ChatClient.ResetServerString();
		//The string contains more chars that not digits or empty.
		} else {
			lblErr.setText("Please enter id that contains ONLY digits");
		}
	}

	/** This method is getting a string of subscriber and loading that data to the GUI
	 * enabling the only fields that we wants that the user will update.
	 * @param Subscriber
	 */
	public void LoadDetails(String orderDetails) {

	    String[] parts = orderDetails.split(", ");

	    this.txtOrderNumber.setText(parts[0]);      // order_number
	    this.txtOrderDate.setText(parts[1]);        // order_date
	    this.txtGuests.setText(parts[2]);           // number_of_guests
	    this.txtConfirm.setText(parts[3]);          // confirmation_code
	    this.txtSubscriberId.setText(parts[4]);     // subscriber_id
	    this.txtPlacingDate.setText(parts[5]);      // date_of_placing_order

	    // Allow editing ONLY for the field you want to update:
	    txtOrderNumber.setEditable(false);
	    txtOrderDate.setEditable(false);
	    txtGuests.setEditable(true);      
	    txtConfirm.setEditable(false);
	    txtSubscriberId.setEditable(false);
	    txtPlacingDate.setEditable(false);
	}
	
	/*
	 * This method is for the exit button sending a message to the server that now we are disconnecting,
	 * closing the GUI and the connection for the server.
	 */
	public void getExitBtn(ActionEvent event) throws Exception {
		System.out.println("Disconnecting from the Server and ending the program.");
		HashMap<String, String> EndingConnections = new HashMap<String, String>();
		EndingConnections.put("Disconnect", "");
		ClientUI.chat.accept(EndingConnections);
		System.exit(0);
	}
}
