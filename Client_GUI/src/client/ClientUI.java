package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.HashMap;


/**
 * The main entry point for the client application.
 * This class initializes and starts the JavaFX client user interface.
 * It manages the connection setup window and handles client-server connections.
 * 
 * @author Dream Team
 * @version 300.1.5
 */
public class ClientUI extends Application {
	/** The single instance of the ClientController managing client-server communication */
	public static ClientController chat; // only one instance

	/**
	 * Main method to launch the JavaFX application.
	 * 
	 * @param args Command line arguments (not used)
	 * @throws Exception if the application fails to start
	 */
	public static void main(String args[]) throws Exception {
		launch(args);
	} // end main

	/**
	 * Initializes and starts the GUI for the ClientUI.
	 * Loads the connection setup FXML scene and sets up the primary stage.
	 * Also sets up a close request handler to properly disconnect from the server.
	 * 
	 * @param primaryStage The primary stage for the application
	 * @throws Exception if loading the FXML file or initializing the scene fails
	 */
	@Override
	public void start(Stage primaryStage) throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/ConnectionSetup.fxml"));
		Parent root = loader.load();
		Scene scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource("/gui/ConnectionSetup.css").toExternalForm());
		
		// Ensure the connection window is large enough so all controls (including Exit) are fully visible
		primaryStage.setScene(scene);
		primaryStage.setTitle("Connection Setup Tool");
		primaryStage.setWidth(700);
		primaryStage.setHeight(550);
		primaryStage.setMinWidth(650);
		primaryStage.setMinHeight(500);
		
		primaryStage.setOnCloseRequest(closeEvent -> {
			try {
				if (chat != null) {
					System.out.println("Disconnecting from the Server...");
					HashMap<String, String> EndingConnections = new HashMap<String, String>();
					EndingConnections.put("Disconnect", "");
					chat.accept(EndingConnections);
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		
		primaryStage.show();
	}

	/**
	 * Establishes the initial connection to the server.
	 * Creates a new ClientController instance if one doesn't already exist.
	 * 
	 * @param serverip The IP address of the server to connect to
	 * @param port The port number for server communication
	 * @return true if a new connection was successfully established, false if already connected
	 */
	public static boolean StartConnectionWithServer(String serverip, String port) {
		if (chat == null) {
			chat = new ClientController(serverip, Integer.parseInt(port));
			return true;
		} else {
			System.out.println("Already connected to the server.");
			return false;
		}
	}

}
