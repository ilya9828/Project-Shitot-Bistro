package gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Button;
import javafx.stage.Stage;

/**
 * Controller for the Show History screen.
 * Displays subscriber's visit history with created_at, startTime, confirmation_code, and finalAmount.
 */
public class ShowHistoryController {

    @FXML
    private Label lblSubscriberID;
    @FXML
    private Label lblStatus;

    @FXML
    private TableView<String> historyTable;
    @FXML
    private TableColumn<String, String> colCreatedAt;
    @FXML
    private TableColumn<String, String> colStartTime;
    @FXML
    private TableColumn<String, String> colConfirmationCode;
    @FXML
    private TableColumn<String, String> colNumberOfGuests;
    @FXML
    private TableColumn<String, String> colFinalAmount;

    @FXML
    private Button btnBack;

    private String subscriberID;

    /**
     * Initializes the controller and loads visit history.
     */
    @FXML
    public void initialize() {
        subscriberID = UserSessionHelper.getSubscriberID();
        if (subscriberID != null) {
            lblSubscriberID.setText("Subscriber ID: " + subscriberID);
            setupTableColumns();
            loadHistory();
        } else {
            lblStatus.setText("Error: No subscriber ID found. Please log in again.");
            lblStatus.setStyle("-fx-text-fill: #ff6b6b;");
        }
    }

    /**
     * Sets up the table columns to display visit history data.
     */
    private void setupTableColumns() {
        // Format: "created_at|startTime|confirmation_code|number_of_guests|finalAmount"
        colCreatedAt.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split("\\|");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 0 ? parts[0] : "");
        });
        
        colStartTime.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split("\\|");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 1 ? parts[1] : "");
        });
        
        colConfirmationCode.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split("\\|");
            return new javafx.beans.property.SimpleStringProperty(parts.length > 2 ? parts[2] : "");
        });
        
        colNumberOfGuests.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split("\\|");
            String guests = parts.length > 3 ? parts[3] : "";
            // Display number of guests or N/A if empty
            if (!guests.isEmpty() && !guests.equals("null") && !guests.equals("NULL")) {
                return new javafx.beans.property.SimpleStringProperty(guests);
            }
            return new javafx.beans.property.SimpleStringProperty("N/A");
        });
        
        colFinalAmount.setCellValueFactory(cellData -> {
            String[] parts = cellData.getValue().split("\\|");
            String amount = parts.length > 4 ? parts[4] : "";
            // Format amount with currency symbol if not empty
            if (!amount.isEmpty() && !amount.equals("null") && !amount.equals("NULL")) {
                return new javafx.beans.property.SimpleStringProperty(amount + " ₪");
            }
            return new javafx.beans.property.SimpleStringProperty("N/A");
        });
    }

    /**
     * Loads visit history from the server.
     */
    private void loadHistory() {
        lblStatus.setText("Loading visit history...");
        lblStatus.setStyle("-fx-text-fill: white;");

        new Thread(() -> {
            try {
                HashMap<String, String> request = new HashMap<>();
                request.put("GetSubscriberHistory", subscriberID);
                ClientUI.chat.accept(request);

                // Wait for server response
                Thread.sleep(500);

                Platform.runLater(() -> {
                    // Response is a List<String> - check ordersTable first (for List responses)
                    List<String> historyList = ChatClient.ordersTable;
                    
                    if (historyList != null && !historyList.isEmpty()) {
                        // Clear the ordersTable for next use
                        ChatClient.ordersTable = new java.util.ArrayList<String>();
                        
                        ObservableList<String> historyData = FXCollections.observableArrayList(historyList);
                        historyTable.setItems(historyData);
                        
                        lblStatus.setText("Loaded " + historyList.size() + " visit(s).");
                        lblStatus.setStyle("-fx-text-fill: #4ade80;");
                    } else {
                        // Check if there's an error message in fromserverString
                        String response = ChatClient.fromserverString;
                        ChatClient.ResetServerString();
                        
                        if (response != null && response.equals("Error")) {
                            lblStatus.setText("Error: Could not load visit history.");
                            lblStatus.setStyle("-fx-text-fill: #ff6b6b;");
                        } else {
                            lblStatus.setText("No visit history found.");
                            lblStatus.setStyle("-fx-text-fill: #ffd700;");
                        }
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblStatus.setText("Error loading visit history: " + e.getMessage());
                    lblStatus.setStyle("-fx-text-fill: #ff6b6b;");
                    e.printStackTrace();
                });
            }
        }).start();
    }

    /**
     * Handles the Back button click.
     * Returns to the Subscriber Menu.
     */
    @FXML
    private void Back(ActionEvent event) throws IOException {
        String menuFile = "/gui/SubMenu.fxml";
        String cssFile = "/gui/SubMenu.css";
        String title = "Subscriber Menu";
        
        FXMLLoader loader = new FXMLLoader(getClass().getResource(menuFile));
        Parent root = loader.load();
        
        // Set the subscriber ID
        try {
            Object controller = loader.getController();
            if (controller instanceof SubMenuController) {
                ((SubMenuController) controller).setSubscriberID(subscriberID);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        Stage stage = new Stage();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());
        stage.setScene(scene);
        stage.setTitle(title);
        
        stage.setOnCloseRequest(closeEvent -> {
            try {
                if (ClientUI.chat != null) {
                    HashMap<String, String> disconnectMsg = new HashMap<>();
                    disconnectMsg.put("Disconnect", "");
                    ClientUI.chat.accept(disconnectMsg);
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        stage.show();
        ((javafx.scene.Node) event.getSource()).getScene().getWindow().hide();
    }
}

