package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

import gui.ConnectionSetupController;

/**This class is starting the clientUI that we see
 * 
 */
public class ClientUI extends Application {
	public static ClientController chat; // only one instance

	public static void main(String args[]) throws Exception {
		launch(args);
	} // end main

	/**
	 *Starting the GUI of the ClientUI  
	 */
	@Override
	public void start(Stage primaryStage) throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/ConnectionSetup.fxml"));
		Parent root = loader.load();
		ConnectionSetupController controller = loader.getController();
		Scene scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource("/gui/ConnectionSetup.css").toExternalForm());
		primaryStage.setScene(scene);
		primaryStage.setTitle("Connection Setup Tool");
		primaryStage.show();
	}

	/**This method is setting the first connection to the server as one instance
	 * @param serverip - server ip that the user wants to connect to
	 * @param port		- port that the user want to communicate with the server 
	 * @return true if new connection was up right now and succeed.
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
