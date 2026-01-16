package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * This class controls the Add Table screen.
 * Allows staff to add new tables to the restaurant.
 */
public class AddTableController {

    @FXML
    private TextField tableIdTextField;

    @FXML
    private TextField capacityTextField;

    @FXML
    private Button addTableButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Button btnBack;

    /**
     * Initialize the controller - disable table ID field and load next available ID
     */
    @FXML
    public void initialize() {
        // Disable and grey out the table ID text field
        tableIdTextField.setEditable(false);
        tableIdTextField.setDisable(true);
        
        // Load the next available table ID from the server
        loadNextTableId();
    }

    /**
     * Load the next available table ID from the database
     */
    private void loadNextTableId() {
        statusLabel.setText("Loading next table ID...");
        
        HashMap<String, String> request = new HashMap<>();
        request.put("GetNextTableId", "");
        
        ClientUI.chat.accept(request);
        
        String response = ChatClient.fromserverString;
        if (response != null && !response.isEmpty() && !response.startsWith("Error")) {
            tableIdTextField.setText(response);
            statusLabel.setText("");
        } else {
            statusLabel.setText("Failed to load table ID. Please try again.");
            tableIdTextField.setText("?");
        }
        
        ChatClient.ResetServerString();
    }

    /**
     * Handle the Add Table button click - validates and saves table to database
     */
    @FXML
    private void handleAddTable() {
        String capacityText = capacityTextField.getText().trim();

        // Validate capacity field
        if (capacityText.isEmpty()) {
            statusLabel.setText("Please enter the number of chairs.");
            showError("Please enter the number of chairs.");
            return;
        }

        // Validate capacity is a positive number
        int capacity;
        try {
            capacity = Integer.parseInt(capacityText);
            if (capacity <= 0) {
                statusLabel.setText("Capacity must be greater than 0.");
                showError("Capacity must be greater than 0.");
                return;
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Please enter a valid number for capacity.");
            showError("Please enter a valid number for capacity.");
            return;
        }

        statusLabel.setText("Adding table... Please wait...");

        // Create request to send to server
        // Format: capacity
        HashMap<String, String> tableData = new HashMap<>();
        tableData.put("AddTable", String.valueOf(capacity));

        ClientUI.chat.accept(tableData);

        String response = ChatClient.fromserverString;
        if ("TableAdded".equals(response)) {
            statusLabel.setText("");
            showInfo("Table added successfully!");
            clearFields();
            loadNextTableId(); // Reload next table ID
        } else {
            statusLabel.setText("");
            String errorMsg = "Failed to add table. ";
            if (response != null && !response.isEmpty()) {
                errorMsg += "Server response: " + response;
            } else {
                errorMsg += "Please try again.";
            }
            showError(errorMsg);
        }

        ChatClient.ResetServerString();
    }

    /**
     * Clear all input fields after successful save
     */
    private void clearFields() {
        capacityTextField.setText("");
    }

    /**
     * Handles the Back button click.
     * Closes the current screen and navigates back to the appropriate menu.
     * 
     * @param event The click event on the back button
     * @throws IOException If navigation fails
     */
    public void Back(ActionEvent event) throws IOException {
        UserSessionHelper.navigateBackToMenu((Node) event.getSource());
    }


    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}

