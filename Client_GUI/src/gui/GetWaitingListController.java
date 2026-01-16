package gui;

import java.io.IOException;
import java.util.List;

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
     * Handles the Back button click.
     * Closes the current screen and navigates back to the appropriate menu.
     * 
     * @param event The click event on the back button
     * @throws IOException If navigation fails
     */
    @FXML
    public void Back(ActionEvent event) throws IOException {
        UserSessionHelper.navigateBackToMenu((Node) event.getSource());
    }

    /**
     * Loads a list of waiting entries and displays them in the table view.
     * 
     * @param waitingEntries List of waiting entries as strings (format: "waitingID, number_of_guests, phone, date, status, created_at")
     */
    public void loadWaitingList(List<String> waitingEntries) {
        // Convert the list to an observable list and set it to the table
        waitingListTable.setItems(FXCollections.observableArrayList(waitingEntries));
    }
    
}

