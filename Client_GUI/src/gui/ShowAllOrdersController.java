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
 * This class is showing us the GUI with table of all the orders
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
    
    /**
     * Initializes the controller.
     * Sets up the table columns to display order data.
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

    
    /**
     * Handles the Back button click.
     * Closes the current screen and navigates back to the menu.
     * 
     * @param event The click event on the back button
     * @throws IOException If navigation fails
     */
    public void Back(ActionEvent event) throws IOException {
        FXMLLoader ordersLoader = new FXMLLoader(getClass().getResource("/gui/Menu.fxml"));
        Parent ordersRoot = ordersLoader.load();
        Stage ordersStage = new Stage();
        Scene ordersScene = new Scene(ordersRoot);
        ordersScene.getStylesheets().add(getClass().getResource("/gui/Menu.css").toExternalForm());
        ordersStage.setScene(ordersScene);
        ordersStage.setTitle("Menu");
        ordersStage.show();
        ((Node) event.getSource()).getScene().getWindow().hide();
    }

    
    
    /**
     * Loads a list of orders and displays them in the table view.
     * 
     * @param orders List of orders as strings (format: "order_number, order_date, number_of_guests, confirmation_code, subscriber_id, date_of_placing_order")
     */
    public void loadOrders(List<String> orders) {
        // Convert the list to an observable list and set it to the table
        ordersTable.setItems(FXCollections.observableArrayList(orders));
    }
    
	/**
	 * Handles the Exit button click.
	 * Sends a disconnect message to the server, closes the GUI, and terminates the connection.
	 * 
	 * @param event The click event on the exit button
	 * @throws Exception If an error occurs during disconnection
	 */
	public void getExitBtn(ActionEvent event) throws Exception {
		System.out.println("Disconnecting from the Server and ending the program.");
		HashMap<String, String> EndingConnections = new HashMap<String, String>();
		EndingConnections.put("Disconnect", "");
		ClientUI.chat.accept(EndingConnections);
		System.exit(0);
	}
}
