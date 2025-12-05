package gui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
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

import java.net.InetAddress;

import Server.EchoServer;
import Server.ServerUI;

/*
 * This is the main frame that ask the user to enter port to listen it to clients
 */
public class ServerPortFrameController  {
	
	@FXML
	private Button btnExit = null;
	@FXML
	private Button btnDone = null;
	
	@FXML
	private Label lblport;
	@FXML
	private Label lbllocalip;
	@FXML
	private Label lblErrorMSG;
	
	@FXML
	private TextField portxt;
	@FXML
	private TextField localiptxt;
	
	ObservableList<String> list;
	
	/*
	 * this method is getting the port from the user
	 */
	private String getport() {
		return portxt.getText();			
	}
	
	private void changeString(String s) {
		lblErrorMSG.setText(s);
		PauseTransition pause = new PauseTransition(Duration.seconds(5));
		pause.setOnFinished(event -> {
			Platform.runLater(() -> {
				lblErrorMSG.setText(""); // Clear the text after 5 seconds
			});
		});
		pause.play();
	}
	
	/*
	 * This function is lunching the listen to clients on the port that the user entered.
	 */
	public void Connect(ActionEvent event) throws Exception {
		String p;
		
		p=getport();
		if(p.trim().isEmpty()) {
			changeString("You must enter a port number");
					
		}
		else
		{
			try {
				ServerUI.runServer(p);
				((Node)event.getSource()).getScene().getWindow().hide();
				FXMLLoader Loader = new FXMLLoader(getClass().getResource("/gui/ClientConnectionStatus.fxml"));
		        Parent Root = Loader.load();
		        ClientConnectionStatusController ClientConnectionStatusController = Loader.getController();
	
		        Stage Stage = new Stage();
		     
		        Scene Scene = new Scene(Root);
		        Scene.getStylesheets().add(getClass().getResource("/gui/ClientConnectionStatus.css").toExternalForm());
		        Stage.setScene(Scene);
		        Stage.setTitle("Connections Table");
		        Stage.show();
		        EchoServer.tableScene = Scene;
		        EchoServer.tableStage = Stage;
		        EchoServer.tableController = ClientConnectionStatusController;
			}catch (Exception ex) {
				changeString(ServerUI.errMsg);
			}
			
		}
		
		
	}
	/*
	 * This method is starting this GUI.
	 */
	public void start(Stage primaryStage) throws Exception {	
		Parent root = FXMLLoader.load(getClass().getResource("/gui/ServerPort.fxml"));
				
		Scene scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource("/gui/ServerPort.css").toExternalForm());
		primaryStage.setTitle("Server Listening Tool");
		primaryStage.setScene(scene);
		
		primaryStage.show();		
	}
	/*
	 * This method is closing the program.
	 */
	public void getExitBtn(ActionEvent event) throws Exception {
		System.out.println("exit Server Listening Tool");
		System.exit(0);			
	}
	
	public void initialize() {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            this.lbllocalip.setText("Your ip: \t\t" + localIp); // Set the local IP in the TextField
        } catch (Exception e) {
            e.printStackTrace();
            this.lbllocalip.setText("Unknown IP");
        }
        //localiptxt.setEditable(false);
    }

}