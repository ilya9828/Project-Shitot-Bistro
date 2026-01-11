package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for the Edit Personal Information screen.
 * Allows subscribers to edit their name, email, and phone number.
 */
public class EditPersonalInfoController {

    @FXML
    private Label lblSubscriberID;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblErr;

    @FXML
    private TextField txtName;
    @FXML
    private TextField txtEmail;
    @FXML
    private TextField txtPhone;

    @FXML
    private Button btnUpdate;
    @FXML
    private Button btnBack;

    private String subscriberID;

    /**
     * Initializes the controller and loads subscriber information.
     */
    @FXML
    public void initialize() {
        subscriberID = UserSessionHelper.getSubscriberID();
        if (subscriberID != null) {
            lblSubscriberID.setText("Subscriber ID: " + subscriberID);
            loadSubscriberInfo();
        } else {
            lblErr.setText("Error: No subscriber ID found. Please log in again.");
        }
    }

    /**
     * Loads subscriber information from the server.
     */
    private void loadSubscriberInfo() {
        lblErr.setText("");
        lblStatus.setText("Loading subscriber information...");
        btnUpdate.setDisable(true);

        new Thread(() -> {
            try {
                HashMap<String, String> request = new HashMap<>();
                request.put("GetSubscriberInfo", subscriberID);
                ClientUI.chat.accept(request);

                // Wait for server response
                Thread.sleep(500);

                Platform.runLater(() -> {
                    String response = ChatClient.fromserverString;
                    ChatClient.ResetServerString();

                    if (response != null && !response.equals("Error") && !response.isEmpty()) {
                        // Response format: "name|email|phone"
                        String[] parts = response.split("\\|");
                        if (parts.length == 3) {
                            txtName.setText(parts[0] != null && !parts[0].equals("null") ? parts[0] : "");
                            txtEmail.setText(parts[1] != null && !parts[1].equals("null") ? parts[1] : "");
                            txtPhone.setText(parts[2] != null && !parts[2].equals("null") ? parts[2] : "");
                            lblStatus.setText("");
                            btnUpdate.setDisable(false);
                        } else {
                            lblErr.setText("Error: Invalid response from server.");
                            lblStatus.setText("");
                            btnUpdate.setDisable(false);
                        }
                    } else {
                        lblErr.setText("Error: Could not load subscriber information.");
                        lblStatus.setText("");
                        btnUpdate.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblErr.setText("Error loading subscriber information: " + e.getMessage());
                    lblStatus.setText("");
                    btnUpdate.setDisable(false);
                    e.printStackTrace();
                });
            }
        }).start();
    }

    /**
     * Handles the Update button click.
     * Validates input and sends update request to server.
     */
    @FXML
    private void UpdateBtn(ActionEvent event) {
        String name = txtName.getText().trim();
        String email = txtEmail.getText().trim();
        String phone = txtPhone.getText().trim();

        // Validation
        if (name.isEmpty()) {
            showError("Name cannot be empty.");
            return;
        }

        if (email.isEmpty() && phone.isEmpty()) {
            showError("Please provide at least email or phone number.");
            return;
        }

        // Basic email validation
        if (!email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            showError("Please enter a valid email address.");
            return;
        }

        // Basic phone validation (8-15 digits)
        if (!phone.isEmpty() && !phone.matches("\\d{8,15}")) {
            showError("Please enter a valid phone number (8-15 digits).");
            return;
        }

        lblErr.setText("");
        lblStatus.setText("Updating information...");
        btnUpdate.setDisable(true);

        new Thread(() -> {
            try {
                // Format: "subscriberID|name|email|phone"
                String updateData = subscriberID + "|" + name + "|" + email + "|" + phone;
                
                HashMap<String, String> request = new HashMap<>();
                request.put("UpdateSubscriberInfo", updateData);
                ClientUI.chat.accept(request);

                // Wait for server response
                Thread.sleep(500);

                Platform.runLater(() -> {
                    String response = ChatClient.fromserverString;
                    ChatClient.ResetServerString();

                    if ("Updated".equals(response)) {
                        lblStatus.setText("Information updated successfully!");
                        lblStatus.setStyle("-fx-text-fill: #4ade80;");
                        btnUpdate.setDisable(false);
                    } else {
                        lblErr.setText("Error: Failed to update information. " + (response != null ? response : ""));
                        lblStatus.setText("");
                        btnUpdate.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    lblErr.setText("Error updating information: " + e.getMessage());
                    lblStatus.setText("");
                    btnUpdate.setDisable(false);
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

    /**
     * Shows an error alert dialog.
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

