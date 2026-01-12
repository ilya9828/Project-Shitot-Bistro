package gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import client.ClientUI;
import common.UserSessionHelper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * This class controls the Current Reservations screen.
 * Displays a table of all future reservations (order_time_date >= NOW()) from the orders table.
 */
public class CurrentReservationsController {

    @FXML
    private Button btnExit = null;
    
    @FXML
    private Button btnBack = null;

    @FXML
    private TableView<String> currentReservationsTable;
    
    @FXML
    private TableColumn<String, String> orderNumber;
    
    @FXML
    private TableColumn<String, String> confirmationCode;
    
    @FXML
    private TableColumn<String, String> name;
    
    @FXML
    private TableColumn<String, String> phone;
    
    @FXML
    private TableColumn<String, String> status;
    
    @FXML
    private TableColumn<String, String> orderTimeDate;

    /**
     * Initialize the GUI with the data to the table view.
     */
    @FXML
    public void initialize() {
        // Split each row of data (string) and display it in separate columns
        // Format: "order_number, confirmation_code, name, phone, status, order_time_date"
        orderNumber.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 0 ? parts[0] : "");
        });
        
        confirmationCode.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 1 ? parts[1] : "");
        });
        
        name.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 2 ? parts[2] : "");
        });
        
        phone.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 3 ? parts[3] : "");
        });
        
        status.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 4 ? parts[4] : "");
        });
        
        orderTimeDate.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 5 ? parts[5] : "");
        });
    }

    /**
     * This method is for the back button closing the current GUI and uploading the menu GUI.
     * @param event - click on the back button.
     * @throws IOException
     */
    @FXML
    public void Back(ActionEvent event) throws IOException {
        UserSessionHelper.navigateBackToMenu((Node) event.getSource());
    }

    /**
     * This method is getting list of current reservations and uploading it to the table view
     * @param reservations - list of reservations as strings (format: "order_number, confirmation_code, name, phone, status, order_time_date")
     */
    public void loadCurrentReservations(List<String> reservations) {
        // Convert the list to an observable list and set it to the table
        currentReservationsTable.setItems(FXCollections.observableArrayList(reservations));
    }
    
    /**
     * This method is for the exit button sending a message to the server that now we are disconnecting,
     * closing the GUI and the connection for the server.
     */
    @FXML
    public void getExitBtn(ActionEvent event) throws Exception {
        System.out.println("Disconnecting from the Server and ending the program.");
        HashMap<String, String> EndingConnections = new HashMap<String, String>();
        EndingConnections.put("Disconnect", "");
        ClientUI.chat.accept(EndingConnections);
        System.exit(0);
    }
}

