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
 * This class controls the Current Customers screen.
 * Displays a table of all tables with status "Taken" (occupied).
 */
public class CurrentCustomersController {

    @FXML
    private Button btnExit = null;
    
    @FXML
    private Button btnBack = null;

    @FXML
    private TableView<String> occupiedTablesTable;
    
    @FXML
    private TableColumn<String, String> tableID;
    
    @FXML
    private TableColumn<String, String> capacity;
    
    @FXML
    private TableColumn<String, String> customerName;
    
    @FXML
    private TableColumn<String, String> checkInTime;
    
    @FXML
    private TableColumn<String, String> confirmationCode;

    /**
     * Initialize the GUI with the data to the table view.
     */
    @FXML
    public void initialize() {
        // Split each row of data (string) and display it in separate columns
        // Format: "tableID, capacity, customerName, checkInTime, confirmationCode"
        tableID.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 0 ? parts[0] : "");
        });
        
        capacity.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 1 ? parts[1] : "");
        });
        
        customerName.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 2 ? parts[2] : "");
        });
        
        checkInTime.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 3 ? parts[3] : "");
        });
        
        confirmationCode.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 4 ? parts[4] : "");
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
     * This method is getting list of occupied tables and uploading it to the table view
     * @param tables - list of occupied tables as strings (format: "tableID, capacity, customerName, checkInTime, confirmationCode")
     */
    public void loadOccupiedTables(List<String> tables) {
        // Convert the list to an observable list and set it to the table
        occupiedTablesTable.setItems(FXCollections.observableArrayList(tables));
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

