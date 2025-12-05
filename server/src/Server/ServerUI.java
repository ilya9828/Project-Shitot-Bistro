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
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		ServerPortFrameController aFrame = new ServerPortFrameController();
		aFrame.start(primaryStage);
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
