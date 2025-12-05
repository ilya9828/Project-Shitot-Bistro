package gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Server.EchoServer;
import Server.ServerUI;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty; // Make sure this import is present

/**
 * This class is showing us the GUI of the connections table
 */
public class ClientConnectionStatusController {

	// Observable list to hold connection details as strings
	private static HashMap<String, String> connectionList;

	@FXML
	private Button btnExit;
	@FXML
	private Button btnRefresh;
	@FXML
	private Label lblShowconnection;
	@FXML
	private Label lblNewConnection;

	@FXML
	private TableView<String> ClientConnectionsTable;
	@FXML
	private TableColumn<String, String> ClientIPColumn;
	@FXML
	private TableColumn<String, String> HostNameColumn;
	@FXML
	private TableColumn<String, String> StatusColumn;

	/**
	 * This method is changing the label on the GUI
	 * 
	 * @param s - String of connection that we want to show on the GUI
	 */
	private void changeString(String s) {
		lblNewConnection.setText(s);
		PauseTransition pause = new PauseTransition(Duration.seconds(5));
		pause.setOnFinished(event -> {
			Platform.runLater(() -> {
				lblNewConnection.setText(""); // Clear the text after 5 seconds
			});
		});
		pause.play();
	}

	/**
	 * This method when there is a changing with the clients connections it will
	 * update on the GUI by 2 methods: 1- changing the label. 2-changing the
	 * tableview.
	 * 
	 * @param s - String of connection
	 */
	public void setconnection(String s) {
		Platform.runLater(() -> {
			changeString(s);
		});

		RefreshConnections();
	}

	/**
	 * This method is action for refresh button
	 * 
	 * @param event
	 */
	public void Refreshmethod(ActionEvent event) {
		RefreshConnections();
	}

	/**
	 * This method is refreshing the list of clients status and refreshing the
	 * tableview
	 */
	private void RefreshConnections() {
		connectionList = EchoServer.clientsstatusconnections;
		if (!connectionList.isEmpty()) {
			// Extract only values from the HashMap (ignoring the keys)
			ObservableList<String> clientDataList = FXCollections.observableArrayList();
			for (Map.Entry<String, String> entry : connectionList.entrySet()) {
				// Add the value (which is the "IPaddress, Hostname, Status") to the list
				clientDataList.add(entry.getValue());
			}

			// Load the client data into the table
			loadConnections(clientDataList);

			// For Client IP column
			ClientIPColumn.setCellValueFactory(cellData -> {
				String value = cellData.getValue(); // Get the full string, e.g. "192.168.1.1, ComputerName, Online"
				String clientIp = value.split(", ")[0].trim(); // Extract the IP part
				return new SimpleStringProperty(clientIp); // Return the client IP as a SimpleStringProperty
			});

			// For HostName column
			HostNameColumn.setCellValueFactory(cellData -> {
				String value = cellData.getValue(); // Get the full string
				String hostName = value.split(", ")[1].trim(); // Extract the HostName part
				return new SimpleStringProperty(hostName); // Return the HostName as a SimpleStringProperty
			});

			// For Status column
			StatusColumn.setCellValueFactory(cellData -> {
				String value = cellData.getValue(); // Get the full string
				String status = value.split(", ")[2].trim(); // Extract the Status part
				return new SimpleStringProperty(status); // Return the Status as a SimpleStringProperty
			});
		}
	}

	/*
	 * This method is loading the list of connection to the table view.
	 */
	public void loadConnections(List<String> clients) {
		ClientConnectionsTable.setItems(FXCollections.observableArrayList(clients));
	}

	// Method to handle the exit action
	public void getExitBtn(ActionEvent event) throws Exception {
		System.out.println("Exit Server Tool.");
		System.exit(0);
	}

	/*
	 * When start this GUI we will see the connection port of the server and if
	 * there are connections we will see in the table view.
	 */
	public void initialize() {
		lblShowconnection.setText("Server listening to client on port: " + ServerUI.portServer);
		RefreshConnections();
	}
}
