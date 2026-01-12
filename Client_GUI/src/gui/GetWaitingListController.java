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
 * This class controls the Get Waiting List screen.
 * Displays a table of all entries from the waitingentry table.
 */
public class GetWaitingListController {

    @FXML
    private Button btnExit = null;
    
    @FXML
    private Button btnBack = null;

    @FXML
    private TableView<String> waitingListTable;
    
    @FXML
    private TableColumn<String, String> waitingID;
    
    @FXML
    private TableColumn<String, String> numberOfGuests;
    
    @FXML
    private TableColumn<String, String> phone;
    
    @FXML
    private TableColumn<String, String> date;
    
    @FXML
    private TableColumn<String, String> status;
    
    @FXML
    private TableColumn<String, String> createdAt;

    /**
     * Initialize the GUI with the data to the table view.
     */
    @FXML
    public void initialize() {
        // Split each row of data (string) and display it in separate columns
        // Format: "waitingID, number_of_guests, phone, date, status, created_at"
        waitingID.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 0 ? parts[0] : "");
        });
        
        numberOfGuests.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 1 ? parts[1] : "");
        });
        
        phone.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 2 ? parts[2] : "");
        });
        
        date.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 3 ? parts[3] : "");
        });
        
        status.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split(", ");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 4 ? parts[4] : "");
        });
        
        createdAt.setCellValueFactory(cellData -> {
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
     * This method is getting list of waiting entries and uploading it to the table view
     * @param waitingEntries - list of waiting entries as strings (format: "waitingID, number_of_guests, phone, date, status, created_at")
     */
    public void loadWaitingList(List<String> waitingEntries) {
        // Convert the list to an observable list and set it to the table
        waitingListTable.setItems(FXCollections.observableArrayList(waitingEntries));
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

