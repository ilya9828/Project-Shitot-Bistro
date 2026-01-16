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
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * This class controls the Edit Table screen.
 * Allows staff to load, edit, and delete tables.
 */
public class EditTableController {

    @FXML
    private ComboBox<String> tableIdComboBox;

    @FXML
    private Button loadButton;

    @FXML
    private Button deleteButton;

    @FXML
    private TextField capacityTextField;

    @FXML
    private Button saveButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Button btnBack;

    private int currentCapacity = -1; // Store current capacity to compare with new value
    private String selectedTableId = null;

    /**
     * Initialize the controller - load all table IDs from database
     */
    @FXML
    public void initialize() {
        loadTableIds();
        
        // Initially disable capacity field and save button
        capacityTextField.setDisable(true);
        saveButton.setDisable(true);
    }

    /**
     * Load all table IDs from the database into the dropdown
     */
    private void loadTableIds() {
        statusLabel.setText("Loading tables...");
        
        HashMap<String, String> request = new HashMap<>();
        request.put("GetAllTableIds", "");
        
        ClientUI.chat.accept(request);
        
        String response = ChatClient.fromserverString;
        if (response != null && !response.isEmpty() && !response.startsWith("Error")) {
            // Parse the response (comma-separated list of table IDs)
            String[] tableIds = response.split(",");
            tableIdComboBox.getItems().clear();
            for (String id : tableIds) {
                String trimmedId = id.trim();
                if (!trimmedId.isEmpty()) {
                    tableIdComboBox.getItems().add(trimmedId);
                }
            }
            statusLabel.setText("");
        } else {
            statusLabel.setText("Failed to load tables. Please try again.");
            showError("Failed to load tables from database.");
        }
        
        ChatClient.ResetServerString();
    }

    /**
     * Handle the Load button click - loads the capacity for the selected table
     */
    @FXML
    private void handleLoad() {
        String selectedId = tableIdComboBox.getValue();
        
        if (selectedId == null || selectedId.isEmpty()) {
            statusLabel.setText("Please select a table ID first.");
            showError("Please select a table ID first.");
            return;
        }

        selectedTableId = selectedId;
        statusLabel.setText("Loading table data...");

        // Request table data from server
        HashMap<String, String> request = new HashMap<>();
        request.put("GetTableData", selectedId);
        
        ClientUI.chat.accept(request);
        
        String response = ChatClient.fromserverString;
        if (response != null && !response.isEmpty() && !response.startsWith("Error")) {
            // Parse response: format should be "capacity"
            try {
                currentCapacity = Integer.parseInt(response.trim());
                capacityTextField.setText(String.valueOf(currentCapacity));
                capacityTextField.setDisable(false);
                saveButton.setDisable(false);
                statusLabel.setText("");
            } catch (NumberFormatException e) {
                statusLabel.setText("Error parsing table data.");
                showError("Error loading table data. Please try again.");
            }
        } else {
            statusLabel.setText("Failed to load table data.");
            showError("Failed to load table data. Please try again.");
        }
        
        ChatClient.ResetServerString();
    }

    /**
     * Handle the Delete button click - deletes the selected table
     */
    @FXML
    private void handleDelete() {
        String selectedId = tableIdComboBox.getValue();
        
        if (selectedId == null || selectedId.isEmpty()) {
            statusLabel.setText("Please select a table ID first.");
            showError("Please select a table ID to delete.");
            return;
        }

        // Confirm deletion
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Deletion");
        confirmAlert.setHeaderText("Delete Table");
        confirmAlert.setContentText("Are you sure you want to delete table number " + selectedId + "? , it might effect future reservations");
        
        confirmAlert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        
        confirmAlert.showAndWait();
        
        if (confirmAlert.getResult() == ButtonType.YES) {
            statusLabel.setText("Deleting table... Please wait...");

            HashMap<String, String> request = new HashMap<>();
            request.put("DeleteTable", selectedId);
            
            ClientUI.chat.accept(request);
            
            String response = ChatClient.fromserverString;
            if ("TableDeleted".equals(response)) {
                statusLabel.setText("");
                showInfo("Table " + selectedId + " deleted successfully!");
                // Clear fields and reload table IDs
                clearFields();
                loadTableIds();
            } else {
                statusLabel.setText("");
                String errorMsg = "Failed to delete table. ";
                if (response != null && !response.isEmpty()) {
                    errorMsg += "Server response: " + response;
                } else {
                    errorMsg += "Please try again.";
                }
                showError(errorMsg);
            }
            
            ChatClient.ResetServerString();
        }
    }

    /**
     * Handle the Save button click - saves the updated capacity
     */
    @FXML
    private void handleSave() {
        if (selectedTableId == null) {
            statusLabel.setText("Please load a table first.");
            showError("Please load a table first.");
            return;
        }

        String capacityText = capacityTextField.getText().trim();

        if (capacityText.isEmpty()) {
            statusLabel.setText("Please enter the number of chairs.");
            showError("Please enter the number of chairs.");
            return;
        }

        int newCapacity;
        try {
            newCapacity = Integer.parseInt(capacityText);
            if (newCapacity <= 0) {
                statusLabel.setText("Capacity must be greater than 0.");
                showError("Capacity must be greater than 0.");
                return;
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Please enter a valid number for capacity.");
            showError("Please enter a valid number for capacity.");
            return;
        }

        // Check if the new capacity is different from current capacity
        if (newCapacity == currentCapacity) {
            statusLabel.setText("New capacity must be different from current capacity.");
            showError("New capacity must be different from current capacity (" + currentCapacity + ").");
            return;
        }

        statusLabel.setText("Saving changes... Please wait...");

        // Send update request: format "tableID|capacity"
        String data = selectedTableId + "|" + newCapacity;
        HashMap<String, String> request = new HashMap<>();
        request.put("UpdateTable", data);
        
        ClientUI.chat.accept(request);
        
        String response = ChatClient.fromserverString;
        if ("TableUpdated".equals(response)) {
            statusLabel.setText("");
            showInfo("Table " + selectedTableId + " updated successfully!");
            currentCapacity = newCapacity; // Update current capacity
            clearFields();
        } else {
            statusLabel.setText("");
            String errorMsg = "Failed to update table. ";
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
     * Clear all input fields
     */
    private void clearFields() {
        capacityTextField.setText("");
        capacityTextField.setDisable(true);
        saveButton.setDisable(true);
        currentCapacity = -1;
        selectedTableId = null;
        tableIdComboBox.setValue(null);
    }

    /**
     * This method is for the back button closing the current GUI and uploading the menu GUI.
     * @param event - click on the back button.
     * @throws IOException
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

