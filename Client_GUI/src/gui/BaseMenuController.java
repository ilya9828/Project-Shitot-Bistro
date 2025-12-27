package gui;

import java.io.IOException;
import java.util.HashMap;

import client.ClientUI;
import common.UserSessionHelper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.stage.Stage;

/**
 * Base controller for all menu screens (Guest, Staff, Manager, Subscriber).
 * Contains common functionality for navigation and window management.
 */
public abstract class BaseMenuController {
    
    // Common buttons - should be defined in subclasses' FXML
    @FXML
    protected Button reserveTableButton;

    @FXML
    protected Button editReservationButton;

    @FXML
    protected Button checkInButton;

    @FXML
    protected Button joinWaitingListButton;

    @FXML
    protected Button exitWaitingListButton;

    @FXML
    protected Button payBillButton;

    @FXML
    protected Button backToLoginButton;

    /**
     * Handles the "Reserve Table" button click.
     * Navigates to the ReserveTable screen.
     */
    @FXML
    protected void handleReserveTable() {
        navigateToScreen("ReserveTable", "Reserve Table");
    }

    /**
     * Handles the "Edit Reservation" button click.
     * Navigates to the EditReservation screen.
     */
    @FXML
    protected void handleEditReservation() {
        // Using UpdateOrderDate as EditReservation (or create new screen)
        navigateToScreen("UpdateOrderDate", "Edit Reservation");
    }

    /**
     * Handles the "Check In" button click.
     * Navigates to the CheckIn screen.
     */
    @FXML
    protected void handleCheckIn() {
        navigateToScreen("CheckIn", "Check In");
    }

    /**
     * Handles the "Join Waiting List" button click.
     * Navigates to the WaitingList screen.
     */
    @FXML
    protected void handleJoinWaitingList() {
        navigateToScreen("WaitingList", "Waiting List");
    }

    /**
     * Handles the "Exit Waiting List" button click.
     * FIX: Navigates to ExitWaitingList screen
     */
    @FXML
    protected void handleExitWaitingList() {
        navigateToScreen("ExitWaitingList", "Exit Waiting List");
    }

    /**
     * Handles the "Pay Bill" button click.
     * Navigates to the PayBill screen.
     */
    @FXML
    protected void handlePayBill() {
        navigateToScreen("PayBill", "Pay Bill");
    }

    /**
     * Handles the "Back to Login" button click.
     * Navigates back to the User Identification (login) screen.
     */
    @FXML
    protected void handleBackToLogin() {
        try {
            UserSessionHelper.reset();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/UserIdentification.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/gui/UserIdentification.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("User Identification");

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
            closeCurrentWindow();
        } catch (IOException e) {
            System.err.println("Failed to load User Identification screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generic method to navigate to a screen by name.
     * 
     * @param screenName The name of the FXML file (without .fxml extension)
     * @param title The title for the stage
     */
    protected void navigateToScreen(String screenName, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/" + screenName + ".fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/gui/" + screenName + ".css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle(title);

            // Handle window close
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
            closeCurrentWindow();
        } catch (IOException e) {
            System.err.println("Failed to load " + screenName + " screen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Closes the current window.
     */
    protected void closeCurrentWindow() {
        Stage stage = (Stage) reserveTableButton.getScene().getWindow();
        stage.close();
    }

    /**
     * Shows an info alert dialog.
     * 
     * @param message The info message to display
     */
    protected void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an error alert dialog.
     * 
     * @param message The error message to display
     */
    protected void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

