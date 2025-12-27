package gui;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import client.ChatClient;
import client.ClientUI;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ConnectionSetupController {

	@FXML
	private Button btnExit = null;
	@FXML
	private Button btnConnect = null;

	@FXML
	private Label lblServerip;
	@FXML
	private Label lblLocalip;
	@FXML
	private Label lblinstruction;
	@FXML
	private Label lblport;
	@FXML
	private Label lblerrmsg;
	@FXML
	private Label lblwaitmsg;

	@FXML
	private TextField serveriptxt;
	@FXML
	private TextField porttxt;

	private static String localIp;
	
	//This method is getting the ip that the user wrote in the textfield.
	private String getIP() {
		return serveriptxt.getText();
		
	}
	
	//This method is getting the port that the user wrote in the textfield.
	private String getPort() {
		return porttxt.getText();
	}

	/** This method is changing the message on the String to 10 seconds
	 * @param s - the message we want to see on the GUI
	 */
	private void changeString(String s) {
		Platform.runLater(() -> {
			lblerrmsg.setText(s);
		});
		PauseTransition pause = new PauseTransition(Duration.seconds(10));
		pause.setOnFinished(event -> {
			lblerrmsg.setText("");
		});
		pause.play();
	}

	
	/** This method is for the connect button, it starting the connection to the server 
	 * and upload the next (menu) GUI
	 * @param event			- the button action
	 */
	public void Connect(ActionEvent event){
		String ip, port;
		ip = getIP();
		if (ip.trim().isEmpty()) {

			changeString("You must enter an ip address");
		} else {
			port = getPort();
			if (port.trim().isEmpty()) {

				changeString("You must enter the port");
			} else {
				changeString("Please wait while trying connect to the server...");
				lblwaitmsg.setVisible(true);
				// first the GUI will show the message then we will connect to the server.
				Callable<Void> task = () -> {
					boolean connected = ClientUI.StartConnectionWithServer(ip, port);
					Platform.runLater(() -> {
						if (!connected) {
							changeString("Can't connect to this IP");
						} else {
							HashMap<String, String> connectionHash = new HashMap<>();
							connectionHash.put("Connected", localIp);
							ClientUI.chat.accept(connectionHash);

							if (!ChatClient.fromserverString.equals("Error!")) {
								System.out.println("Connected to the server");
								try {
									FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/UserIdentification.fxml"));
									Parent root = loader.load();
									Stage stage = new Stage();
									Scene scene = new Scene(root);
									scene.getStylesheets()
											.add(getClass().getResource("/gui/UserIdentification.css").toExternalForm());
									stage.setScene(scene);
									stage.setTitle("User Identification");
									
									// טיפול בסגירת חלון דרך האיקס
									stage.setOnCloseRequest(closeEvent -> {
										try {
											if (ClientUI.chat != null) {
												System.out.println("Disconnecting from the Server...");
												HashMap<String, String> EndingConnections = new HashMap<String, String>();
												EndingConnections.put("Disconnect", "");
												ClientUI.chat.accept(EndingConnections);
												// מחכה קצת כדי שההודעה תשלח
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
									
									stage.show();
									((Node) event.getSource()).getScene().getWindow().hide();
								} catch (IOException e) {
									e.printStackTrace();
									changeString("Failed to load Menu screen.");
								}
							} else {
								changeString("Connection failed.");
							}

							ChatClient.ResetServerString();
						}
					});
					return null;
				};
				//After we saw the GUI message we are starting to connect to the server.
				FutureTask<Void> futureTask = new FutureTask<>(task);
				new Thread(futureTask).start();
				try {
					futureTask.get();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}
		}
	}

	
	/**Constructor of this class, starting the GUI
	 * @param primaryStage	-Stage of this GUI to upload all the data.
	 */
	public void start(Stage primaryStage){
	    Parent root;
	    try {
	        root = FXMLLoader.load(getClass().getResource("/gui/ConnectionSetup.fxml"));
	        Scene scene = new Scene(root);
	        scene.getStylesheets().add(getClass().getResource("/gui/ConnectionSetup.css").toExternalForm());
	        primaryStage.setTitle("Connection Setup Tool");
	        primaryStage.setScene(scene);

	        // לוכד סגירת חלון דרך האיקס
	        primaryStage.setOnCloseRequest(event -> {
	            try {
	                handleExit(); // שולח הודעה לשרת לפני סגירה
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	            Platform.exit(); // סוגר את האפליקציה
	        });

	        primaryStage.show();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}
	
	private void handleExit() throws Exception {
	    System.out.println("Disconnecting from server...");
	    // כאן תשים את הקוד לשליחת הודעת "Disconnected" לשרת
	    if (ClientUI.chat != null) {
		    HashMap<String, String> disconnectMsg = new HashMap<>();
		    disconnectMsg.put("Disconnect", "");
		    ClientUI.chat.accept(disconnectMsg);
		    // מחכה קצת כדי שההודעה תשלח
		    try {
		        Thread.sleep(200);
		    } catch (InterruptedException e) {
		        e.printStackTrace();
		    }
	    }

	    System.out.println("Exit Connection Setup Tool");
	}
	
	
	/**This constructor close this system.
	 * @param event - the click on the Exit btn.
	 * @throws Exception
	 */
	public void getExitBtn(ActionEvent event) throws Exception {
	    handleExit();
	    Platform.exit(); // סוגר את ה-GUI
	}




	
	/** init starting automaticly when the GUI is up.
	 * showing the local ip in the GUI.
	 * 
	 */
	public void initialize() {
		try {
			localIp = InetAddress.getLocalHost().getHostAddress();
			lblLocalip.setText("Your IP: " + localIp);
			serveriptxt.setText(localIp);
			
			
		} catch (UnknownHostException e) {
			lblLocalip.setText("Unable to determine the local IP address\n Unknown IP");
			e.printStackTrace();
		}
	}

}
