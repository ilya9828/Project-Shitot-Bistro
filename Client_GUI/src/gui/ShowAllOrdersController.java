package gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import client.ClientUI;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

/**
 * This class is showing us the GUI with table of all the subscribers
 */
public class ShowAllOrdersController {

    @FXML
    private Button btnExit = null;
    @FXML
    private Button btnBack = null;

    @FXML
    private TableView<String> ordersTable;  // Change TableView type to String
    @FXML
    private TableColumn<String, String> order_number;
    @FXML
    private TableColumn<String, String> order_date;
    @FXML
    private TableColumn<String, String> number_of_guests;
    @FXML
    private TableColumn<String, String> confirmation_code;
    @FXML
    private TableColumn<String, String> subscriber_id;
    @FXML
    private TableColumn<String, String> date_of_placing_order;
    /*
	 * initialize the GUI with the data to the table view.
	 */
    public void initialize() {
        // Split each row of data (string) and display it in separate columns
    	order_number.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts[0]);
        });
    	order_date.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts[1]);
        });
    	number_of_guests.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts[2]);
        });
    	confirmation_code.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts[3]);
        });
    	subscriber_id.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts[4]);
        });
    	date_of_placing_order.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts[5]);
        });
    }

    
    /** This method is for the back button closing the current GUI and uploading the menu GUI.
     * @param event - click on the back button.
     * @throws IOException
     */
    public void Back(ActionEvent event) throws IOException {
        FXMLLoader studentLoader = new FXMLLoader(getClass().getResource("/gui/Menu.fxml"));
        Parent studentRoot = studentLoader.load();
        Stage studentStage = new Stage();
        Scene studentScene = new Scene(studentRoot);
        studentScene.getStylesheets().add(getClass().getResource("/gui/Menu.css").toExternalForm());
        studentStage.setScene(studentScene);
        studentStage.setTitle("Menu");
        studentStage.show();
        ((Node) event.getSource()).getScene().getWindow().hide();
    }

    
    
    /**This method is getting list of the subscribers and uploading it to the table view
     * @param orders - list of the orders.
     */
    public void loadOrders(List<String> orders) {
        // Convert the list to an observable list and set it to the table
        ordersTable.setItems(FXCollections.observableArrayList(orders));
    }
    
	/*
	 * This method is for the exit button sending a message to the server that now we are disconnecting,
	 * closing the GUI and the connection for the server.
	 */
	public void getExitBtn(ActionEvent event) throws Exception {
		System.out.println("Disconnecting from the Server and ending the program.");
		HashMap<String, String> EndingConnections = new HashMap<String, String>();
		EndingConnections.put("Disconnect", "");
		ClientUI.chat.accept(EndingConnections);
		System.exit(0);
	}
}
