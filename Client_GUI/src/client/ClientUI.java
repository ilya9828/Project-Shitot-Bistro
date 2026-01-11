package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.HashMap;


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
