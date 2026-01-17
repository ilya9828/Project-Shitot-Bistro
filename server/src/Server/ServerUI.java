package Server;

import javafx.application.Application;
import javafx.stage.Stage;
import gui.ServerPortFrameController;

/**
 * The main entry point for the server application.
 * This class initializes and starts the JavaFX server user interface.
 * Manages server startup, port configuration, and database connection.
 * 
 * @author Dream Team
 * @version 300.1.5
 */
public class ServerUI extends Application {
	/** The port number the server is listening on */
	public static String portServer;
	
	/** Flag indicating whether the server is currently running */
	public static boolean Running = false;
	
	/** Error message string for displaying server startup errors */
	public static String errMsg = "";

	/**
	 * Main method to launch the JavaFX server application.
	 * 
	 * @param args Command line arguments (not used)
	 * @throws Exception if the application fails to start
	 */
	public static void main(String args[]) throws Exception {
		try {
			launch(args);
		} catch (Exception e) {
			System.err.println("Error starting server application: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * Initializes and starts the server GUI.
	 * Loads the server port configuration frame to allow the user to specify the port.
	 * 
	 * @param primaryStage The primary stage for the application
	 * @throws Exception if initializing the server UI fails
	 */
	@Override
	public void start(Stage primaryStage) throws Exception {
		try {
			ServerPortFrameController aFrame = new ServerPortFrameController();
			aFrame.start(primaryStage);
		} catch (Exception e) {
			System.err.println("Error initializing server UI: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * Starts the server with the specified port number.
	 * Initializes the EchoServer, establishes database connection, and begins listening for clients.
	 * Generates monthly reports if needed.
	 * 
	 * @param p The port number as a string to start the server on
	 * @throws Exception if the server fails to start (invalid port, connection error, etc.)
	 */
	public static void runServer(String p) throws Exception {
		int port = 0; // Port to listen on
		String localerr = "";
		try {
			port = Integer.parseInt(p); // Set port
			portServer = p;
		} catch (Throwable t) {
			localerr = localerr + ("ERROR - Could not connect!");
		}
		EchoServer sv = new EchoServer(port);
		try {
			sv.listen();
			String str = mysqlConnection.connectToDB();
			if(str.contains("failed") || str.contains("SQLException")) {
				localerr = localerr + ("ERROR - Could not connect to SQL\n");
			}
			else {
				Running = true;
				// Generate monthly reports for previous month if they don't exist
				// (This is also called in connectToDB, but calling here as backup)
				try {
					mysqlConnection.generateMonthlyReportsIfNeeded();
				} catch (Exception e) {
					System.err.println("Warning: Failed to generate monthly reports: " + e.getMessage());
				}
			}
		} catch (Exception ex) {
			localerr = localerr + ("ERROR - Could not listen for clients!");
		}
			if (!Running ) {
				errMsg = localerr;
				throw new Exception(localerr);
			}
		
	}

}
