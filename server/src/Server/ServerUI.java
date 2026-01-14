package Server;

import javafx.application.Application;
import javafx.stage.Stage;
import gui.ServerPortFrameController;

/*
 * This class is the server interface to the G21-prototype-server
 */
public class ServerUI extends Application {
	public static String portServer;
	public static boolean Running = false;
	public static String errMsg = "";

	public static void main(String args[]) throws Exception {
		try {
			launch(args);
		} catch (Exception e) {
			System.err.println("Error starting server application: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

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
