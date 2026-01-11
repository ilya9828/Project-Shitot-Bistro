package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import entities.Reservations;
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
 * This class is showing the GUI of updating number of guests per order.
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
	private TextField txtConCode;
	@FXML 
	private TextField txtOrderDate;
	@FXML 
	private TextField txtOrderName;
	@FXML 
	private TextField guestsField;
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
	 * which order to update and what to change.
	 * 
	 * @param event - the click on the update button.
	 */
	public void UpdateBtn(ActionEvent event) {

	    if (loaded <= 0) {
	        lblErr.setText("You must load an Order first.");
	        return;
	    }

	    // Build update data
	    HashMap<String, String> updateHashMap = new HashMap<>();
	    String newInfo = txtConCode.getText() + " " + txtGuests.getText();
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
	 * This method is loading orders. getting the string from the
	 * server and calling other method "LoadDetails()" to handle it and load into the GUI
	 * 
	 * @param event - the click on the load button.
	 */
	public void Loadbtn(ActionEvent event) {
	    String idnumber = txtConCode.getText();

	    // check input: digits only
	    if (!idnumber.matches("\\d+")) {
	        lblErr.setText("Please enter id that contains ONLY digits");
	        return;
	    }

	    // send request to server
	    HashMap<String, String> request = new HashMap<>();
	    request.put("LoadOrders", idnumber);
	    ClientUI.chat.accept(request);

	    // wait for server response
	    while (ChatClient.fromserverReservation == null) {
	        try {
	            Thread.sleep(100);
	        } catch (InterruptedException e) {
	            e.printStackTrace();
	        }
	    }

	    Reservations res = ChatClient.fromserverReservation;

	    // validate received reservation
	    if (res.getOrderNumber() == null) {
	        lblErr.setText("Cant load the requested ID. Please make sure you entered the right id.");
	    } else {
	        lblErr.setText("Requested ID loaded.");
	        loaded = 1;
	        LoadDetails(res);   // <-- load using Reservations object
	    }

	    ChatClient.resetReservation();
	}


	/** This method is getting a string of an order and loading that data to the GUI
	 * enabling the only fields that we wants that the user will update.
	 * @param orderDetails
	 */
	public void LoadDetails(Reservations res) {

	    // Confirmation code – לא ניתן לעריכה
	    txtConCode.setText(res.getConfirmationCode());
	    txtConCode.setEditable(false);

	    // Date
	    if (res.getOrderDateTime() != null) {
	        datePicker.setValue(res.getOrderDateTime().toLocalDate());
	    }

	    // Time
	    if (res.getOrderDateTime() != null) {
	        String time = res.getOrderDateTime()
	                .toLocalTime()
	                .withSecond(0)
	                .withNano(0)
	                .toString();
	        timeComboBox.setValue(time);
	    }

	    // Editable fields
	    guestsField.setText(String.valueOf(res.getNumberOfGuests()));
	    guestsField.setEditable(true);

	    txtOrderName.setText(res.getName());
	    nameField.setEditable(true);

	    phoneField.setText(res.getPhoneNumber());
	    phoneField.setEditable(true);

	    emailField.setText(res.getEmail());
	    emailField.setEditable(true);
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
