package gui;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import client.ChatClient;
import client.ClientUI;
import common.UserSelect;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * This class is the GUI that we showing to the user.
 */
public class MenuController {

	@FXML
	private Button btnExit = null;

	@FXML
	private ComboBox<String> Userselect;

	@FXML
	private Button btnSelect = null;

	@FXML
	private TextField idtxt;

	@FXML
	private Label lblConnected;
	@FXML
	private Label lblinstructions;
	@FXML
	private Label lblmsg;

	/*
	 * This method is setting the UserSelect to the user menu
	 */
	private void setUserselectComboBox() {
		UserSelect SelectOptions[] = UserSelect.values();
		for (int i = 0; i < SelectOptions.length; i++) {
			Userselect.getItems().add(SelectOptions[i].getDisplayName());
		}
		Userselect.setValue(UserSelect.ShowAllOrders.getDisplayName()); // Default selected value
	}

	/*
	 * initialize the gui with the combo box options
	 */
	public void initialize() {
		setUserselectComboBox();
	}

	/**
	 * This method is returning the userselect as a string
	 * 
	 * @return
	 */
	private String getSelection() {
		return (String) Userselect.getValue();
	}

	/**
	 * This method is for the select option from the menu getting the event of
	 * clicking on the btn and sending the selected option to the server and loading
	 * the selected gui.
	 * 
	 * @param event
	 * @throws Exception
	 */
	public void Select(ActionEvent event) throws Exception {
		// getting the selection
		String userselect = getSelection();
		userselect = userselect.replace(" ", "");

		// uploading the gui of the selected option by the user
		FXMLLoader loader = new FXMLLoader();
		((Node) event.getSource()).getScene().getWindow().hide();
		Stage primaryStage = new Stage();
		Pane root = loader.load(getClass().getResource("/gui/" + UserSelect.valueOf(userselect) + ".fxml").openStream());
		UserSelect x = UserSelect.getSelectionFromEnumName(userselect);

		/*
		 * for each select we will send something else for the server and gets diffrent
		 * returns
		 */
		switch (x) {

		case ShowAllOrders:
			lblmsg.setText("Wait while loading the DB...");
			String key = userselect;
			Callable<Void> task = () -> {
				Platform.runLater(() -> {
					HashMap<String, String> ShowTheTable = new HashMap<String, String>();
					ShowTheTable.put(key, "");
					ClientUI.chat.accept(ShowTheTable);
					ShowAllOrdersController subtable = loader.getController();

					// Check if the subscribers table is empty
					if (ChatClient.ordersTable == null || ChatClient.ordersTable.isEmpty()) {
						System.out.println("Empty Table=" + ChatClient.ordersTable);
					} else { // Pass the table directly to the controller
						subtable.loadOrders(ChatClient.ordersTable);
					}
				});
				return null;
			};
			FutureTask<Void> futureTask = new FutureTask<>(task);
			new Thread(futureTask).start();
			futureTask.get();
			break;
			
		case UpdateOrderDate:
			break;

		case UpdateNumberOfGuests:
			break;
			
		case ReserveTable:
			break;

		default:
			System.out.println("error? you selected: " + userselect);
			break;
		}
		// continue show the gui of the selected option by the user.
		Scene scene = new Scene(root);
		scene.getStylesheets()
				.add(getClass().getResource("/gui/" + UserSelect.valueOf(userselect) + ".css").toExternalForm());
		primaryStage.setTitle(userselect + " Tool");
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	/**
	 * This method is starting automaticly when this menu gui is up.
	 * 
	 * @param primaryStage
	 * @throws Exception
	 */
	public void start(Stage primaryStage) throws Exception {
		Parent root = FXMLLoader.load(getClass().getResource("/gui/Menu.fxml"));
		Scene scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource("/gui/Menu.css").toExternalForm());
		primaryStage.setTitle("Menu");
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	/*
	 * This method is for the exit button sending a message to the server that now
	 * we are disconnecting, closing the GUI and the connection for the server.
	 */
	public void getExitBtn(ActionEvent event) throws Exception {
		System.out.println("Disconnecting from the Server and ending the program.");
		HashMap<String, String> EndingConnections = new HashMap<String, String>();
		EndingConnections.put("Disconnect", "");
		ClientUI.chat.accept(EndingConnections);
		System.exit(0);
	}

}
