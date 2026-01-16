package gui;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Random;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import entities.Reservations;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;

/**
 * Controller for the Reserve Table screen.
 * Allows users (guests and subscribers) to make table reservations.
 * For subscribers, automatically uses their stored information (name, phone, email).
 */
public class ReserveTableController {

    @FXML
    private DatePicker datePicker;

    @FXML
    private ComboBox<String> timeComboBox;

    @FXML
    private TextField guestsField;

    @FXML
    private TextField name;
    @FXML
    private TextField input;
    
    @FXML
    private javafx.scene.control.Label lblName;
    @FXML
    private javafx.scene.control.Label lblContact;
    

    @FXML
    private Button reserveButton;
    @FXML
    Labeled lblStatus = null;
    @FXML
    private Button btnBack = null;

    @FXML
    public void initialize() {
        initTimeComboBox(null, null);
        
        // Add listener to date picker to update time combobox when date changes
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                updateTimeComboBoxForDate(newValue);
            }
        });
        
        // If current user is a subscriber, hide name and contact fields.
        if (UserSessionHelper.isSubscriber()) {
        	if (name != null) {
        		name.setVisible(false);
        		name.setManaged(false);
        	}
        	if (input != null) {
        		input.setVisible(false);
        		input.setManaged(false);
        	}
        	if (lblName != null) {
        		lblName.setVisible(false);
        		lblName.setManaged(false);
        	}
        	if (lblContact != null) {
        		lblContact.setVisible(false);
        		lblContact.setManaged(false);
        	}
        }
    }

    /**
     * Initializes the time ComboBox with time slots in 30-minute intervals.
     * 
     * @param startTime The start time for the time slots (defaults to 12:00 if null)
     * @param endTime The end time for the time slots (defaults to 22:00 if null)
     */
    private void initTimeComboBox(LocalTime startTime, LocalTime endTime) {
        timeComboBox.getItems().clear();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        // Use default hours if not provided
        if (startTime == null) {
            startTime = LocalTime.of(12, 0);
        }
        if (endTime == null) {
            endTime = LocalTime.of(22, 0);
        }

        LocalTime time = startTime;
        while (!time.isAfter(endTime)) {
            timeComboBox.getItems().add(time.format(formatter));
            time = time.plusMinutes(30);
        }
    }
    
    /**
     * Updates the time ComboBox based on the selected date.
     * Fetches opening hours from the server for the selected date and populates available time slots.
     * 
     * @param date The selected date for which to fetch opening hours
     */
    private void updateTimeComboBoxForDate(LocalDate date) {
        // Format date as yyyy-MM-dd
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // Send request to server in background thread
        new Thread(() -> {
            ChatClient.ResetServerString();
            
            // Send request to server
            HashMap<String, String> request = new HashMap<>();
            request.put("GetOpeningHours", dateStr);
            ClientUI.chat.accept(request);
            
            // Wait for server response
            while (ChatClient.fromserverString.equals(new String())) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
            
            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                String response = ChatClient.fromserverString;
                ChatClient.ResetServerString();
                
                if ("DEFAULT".equals(response) || response == null || response.isEmpty()) {
                    // Use default hours (12:00-22:00)
                    initTimeComboBox(null, null);
                } else {
                    // Parse opening hours (format: "15:00-18:00")
                    try {
                        String[] times = response.split("-");
                        if (times.length == 2) {
                            LocalTime start = LocalTime.parse(times[0].trim());
                            LocalTime end = LocalTime.parse(times[1].trim());
                            initTimeComboBox(start, end);
                        } else {
                            // Invalid format, use default
                            initTimeComboBox(null, null);
                        }
                    } catch (Exception e) {
                        // Parse error, use default
                        initTimeComboBox(null, null);
                    }
                }
            });
        }).start();
    }
    
    /**
     * Handles the Back button click.
     * Closes the current screen and navigates back to the appropriate menu.
     * 
     * @param event The click event on the back button
     * @throws IOException If navigation fails
     */
    public void Back(ActionEvent event) throws IOException {
        // Use the existing navigation logic that handles all user types
        UserSessionHelper.navigateBackToMenu((Node) event.getSource());
    }
    
    

    /**
     * Handles the Reserve button click.
     * Validates input and sends reservation request to the server.
     */
    @FXML
    private void handleReserve() {

        LocalDate reservationDate = datePicker.getValue();
        String time = timeComboBox.getValue();
        String guestsText = guestsField.getText();
        String email = "";
        String phoneNumber = "";
        String nameText = name.getText();
        
        boolean isSubscriber = UserSessionHelper.isSubscriber();
        String inputText = isSubscriber ? "" : input.getText(); // only from guest
        
        // For guest: validate email/phone input
        if (!isSubscriber) {
	        if (inputText.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
	            email = inputText;
	            phoneNumber = "";
	        } 
	        else if (inputText.matches("\\d{10,12}")) {
	            phoneNumber = inputText;
	            email = "";
	        } 
	        else {
	            email = "";
	            phoneNumber = "";
	            showError("Invalid input");
	            return;
	        }
        }

        // Common validation
		if (reservationDate == null || time == null || guestsText.isEmpty() ||
			(!isSubscriber && (inputText == null || inputText.isEmpty()))) {
            lblStatus.setText("Please fill all required fields.");
            return;
        }

        int numberOfGuests;
        try {
            numberOfGuests = Integer.parseInt(guestsText);
        } catch (NumberFormatException e) {
            lblStatus.setText("Guests must be a number.");
            return;
        }

        Integer subscriberId = null;
        boolean isSubscriberFlag = false;
        
        if (isSubscriber) {
        	try {
        		subscriberId = Integer.parseInt(UserSessionHelper.getSubscriberID());
        		isSubscriberFlag = true;
        	} catch (NumberFormatException ex) {
        		// If subscriber id is not numeric, treat as guest
        		subscriberId = null;
        		isSubscriberFlag = false;
        	}
        }

        String confirmationCode = generateConfirmationCode();
        String orderNumber = "ORD-" + System.currentTimeMillis();

        LocalDateTime reservationDateTime =
                LocalDateTime.of(reservationDate, LocalTime.parse(time));

        LocalDateTime orderDateTime = LocalDateTime.now();
        

        if (reservationDateTime.isBefore(orderDateTime.plusHours(1))) {
            showError("Reservation must be at least 1 hour from now.");
            return;
        }

        if (reservationDateTime.isAfter(orderDateTime.plusMonths(1))) {
            showError("Reservation can be made up to 1 month in advance.");
            return;
        }


        Reservations reservation = new Reservations(
                subscriberId,
                numberOfGuests,
                confirmationCode,
                orderNumber,
                reservationDateTime,
                orderDateTime,
                "PENDING",
                isSubscriberFlag,
                email,
                phoneNumber,
                nameText
                
        );

        lblStatus.setText("Sending reservation to server...\nPlease wait...");

        new Thread(() -> {
            // Reset response string before sending request
            ChatClient.ResetServerString();
            
            // Send request to server
            ClientUI.chat.accept(reservation);
            
            // Wait for server response (wait until string is no longer empty)
            while (ChatClient.fromserverString.equals(new String())) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                String response = ChatClient.fromserverString;
                ChatClient.ResetServerString();
                
                if ("ReservationAdded".equals(response)) {
                    lblStatus.setText("");
                	showInfo(
                            "Reservation created successfully!\n" +
                            "Confirmation Code: " + confirmationCode
                    );
                } else if (response.startsWith("INVALID_DATE_TIME:")) {
                    // Invalid date/time error
                    String errorMessage = response.substring("INVALID_DATE_TIME:".length());
                    showError(errorMessage);
                } else if (response.startsWith("NO_CAPACITY:")) {
                    // Parse alternative times from response
                    String altTimesPart = response.substring("NO_CAPACITY:".length());
                    String message = "Unfortunately, there is no available space at the requested time.\n\n";
                    
                    if (altTimesPart.startsWith("ALT_TIMES:")) {
                        String timesStr = altTimesPart.substring("ALT_TIMES:".length());
                        String[] times = timesStr.split("\\|");
                        String beforeTime = times.length > 0 && !times[0].isEmpty() ? formatAlternativeTime(times[0]) : null;
                        String afterTime = times.length > 1 && !times[1].isEmpty() ? formatAlternativeTime(times[1]) : null;
                        
                        message += "Alternative available times:\n";
                        if (beforeTime != null) {
                            message += "• Earlier: " + beforeTime + "\n";
                        }
                        if (afterTime != null) {
                            message += "• Later: " + afterTime + "\n";
                        }
                        if (beforeTime == null && afterTime == null) {
                            message += "No alternative times found. Please try a different date.\n";
                        }
                    } else {
                        message += "No alternative times found. Please try a different date.\n";
                    }
                    
                    showError(message);
                } else if (response.isEmpty()) {
                    lblStatus.setText("No response from server. Please try again.");
                } else {
                    lblStatus.setText("Failed to create reservation. Server response: " + response);
                }
            });

        }).start();
    }

    /**
     * Generates a random 6-character confirmation code.
     * Uses alphanumeric characters (excluding confusing characters like I, O, 0, 1).
     * 
     * @return A randomly generated confirmation code string
     */
    private String generateConfirmationCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; 
        StringBuilder code = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
        
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("No Available Space");
        alert.setHeaderText("Reservation Not Available");
        
        // Create a label with wrapping text for better display
        javafx.scene.control.Label label = new javafx.scene.control.Label(msg);
        label.setWrapText(true);
        label.setPrefWidth(500); // Set preferred width for wrapping
        label.setStyle("-fx-font-size: 14px; -fx-line-spacing: 5px;");
        
        alert.getDialogPane().setContent(label);
        
        // Increase dialog size
        alert.getDialogPane().setPrefWidth(550);
        alert.getDialogPane().setPrefHeight(300);
        
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    /**
     * Format alternative time from database timestamp to user-friendly format
     * @param timestampStr Timestamp string from database
     * @return Formatted time string (date and time)
     */
    private String formatAlternativeTime(String timestampStr) {
        try {
            // Parse the timestamp string (format: yyyy-MM-dd HH:mm:ss.0)
            LocalDateTime dateTime = LocalDateTime.parse(timestampStr.substring(0, timestampStr.indexOf('.')), 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            // If parsing fails, return the original string
            return timestampStr;
        }
    }

}
