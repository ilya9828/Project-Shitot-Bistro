package gui;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Controller for editing reservations.
 * Allows users to update reservation date, time, and number of guests.
 */
public class EditReservationController {

	@FXML private Label lblOrderDate;
	@FXML private Label lblGuests;

	@FXML private Label lblStatus;
	@FXML private Label lblErr;

	@FXML private DatePicker datePicker;
	@FXML private ComboBox<String> timeComboBox;
	@FXML private TextField txtGuests;
	@FXML private TextField txtConfirm;

	@FXML private Button btnLoad;
	@FXML private Button btnUpdate;
	@FXML private Button btnCancel;
	@FXML private Button btnBack;


	private int loaded = 0;
	private LocalDateTime originalDateTime; // Store original date+time for combining with new time
	
	@FXML
	public void initialize() {
		initTimeComboBox();
	}
	
	/**
	 * Initialize the time ComboBox with 30-minute intervals from 12:00 to 22:00
	 */
	private void initTimeComboBox() {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
		LocalTime time = LocalTime.of(12, 0);
		LocalTime end = LocalTime.of(22, 0);
		
		while (!time.isAfter(end)) {
			timeComboBox.getItems().add(time.format(formatter));
			time = time.plusMinutes(30);
		}
	}

	/**
	 * This method is for the back button closing the current GUI and uploading the
	 * menu GUI.
	 * 
	 * @param event - click on the back button.
	 * @throws IOException
	 */
	
	public void Back(ActionEvent event) throws IOException {
        // Navigate back to appropriate menu based on user type
        boolean isGuest = UserSessionHelper.isGuest();
        String menuFile = isGuest ? "/gui/GuestMenu.fxml" : "/gui/SubMenu.fxml";
        String cssFile = isGuest ? "/gui/GuestMenu.css" : "/gui/SubMenu.css";
        String title = isGuest ? "Guest Menu" : "Subscriber Menu";
        
        FXMLLoader loader = new FXMLLoader(getClass().getResource(menuFile));
        Parent root = loader.load();
        
        // If subscriber, set the subscriber ID
        if (!isGuest) {
            try {
                Object controller = loader.getController();
                if (controller instanceof SubMenuController) {
                    ((SubMenuController) controller).setSubscriberID(UserSessionHelper.getSubscriberID());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        ((Node) event.getSource()).getScene().getWindow().hide();
    }

	/**
	 * This method is for the update button is sending the information to the server
	 * which subscriber to update and what to change.
	 * 
	 * @param event - the click on the update button.
	 */
	public void UpdateBtn(ActionEvent event) {

	    if (loaded <= 0) {
	        showError("You must load a confirmation code first.");
	        return;
	    }

	    // Validate inputs
	    LocalDate selectedDate = datePicker.getValue();
	    String selectedTime = timeComboBox.getValue();
	    String guestsText = txtGuests.getText().trim();
	    
	    if (selectedDate == null || selectedTime == null || guestsText.isEmpty()) {
	        showError("Please fill all fields: Date, Time, and Number of Guests.");
	        return;
	    }
	    
	    // Validate number of guests
	    int numberOfGuests;
	    try {
	        numberOfGuests = Integer.parseInt(guestsText);
	        if (numberOfGuests <= 0) {
	            showError("Number of guests must be positive.");
	            return;
	        }
	    } catch (NumberFormatException e) {
	        showError("Number of guests must be a valid number.");
	        return;
	    }
	    
	    // Parse time and combine with selected date
	    try {
	        LocalTime newTime = LocalTime.parse(selectedTime, DateTimeFormatter.ofPattern("HH:mm"));
	        LocalDateTime newDateTime = LocalDateTime.of(selectedDate, newTime);
	        
	        // Validation: Date and time checks
	        LocalDateTime now = LocalDateTime.now();
	        
	        // Check if date is in the past
	        if (newDateTime.toLocalDate().isBefore(now.toLocalDate())) {
	            showError("Reservation date cannot be in the past.\n\nPlease select a date from today onwards.");
	            return;
	        }
	        
	        // Check if date is more than 1 month ahead
	        LocalDate maxDate = now.toLocalDate().plusMonths(1);
	        if (selectedDate.isAfter(maxDate)) {
	            showError("Reservation date cannot be more than 1 month in advance.\n\nPlease select a date within the next month.");
	            return;
	        }
	        
	        // Check if time is at least 1 hour from now
	        if (newDateTime.isBefore(now.plusHours(1))) {
	            showError("Reservation must be at least 1 hour from now.\n\nYou can only change the reservation to a time that is at least 1 hour in the future.");
	            return;
	        }
	        
	        // Update both date and number of guests, using the confirmation code entered at the top
	        String confirmationCode = txtConfirm.getText();
	        
	        // Update date
	        Timestamp newTimestamp = Timestamp.valueOf(newDateTime);
	        HashMap<String, String> updateDateHashMap = new HashMap<>();
	        String newDateInfo = confirmationCode + " " + newTimestamp.toString();
	        updateDateHashMap.put("UpdateOrderDate", newDateInfo);
	        
	        // Update number of guests
	        HashMap<String, String> updateGuestsHashMap = new HashMap<>();
	        String newGuestsInfo = confirmationCode + " " + numberOfGuests;
	        updateGuestsHashMap.put("UpdateNumberOfGuests", newGuestsInfo);

	        lblStatus.setText("Request sent to the server. Please wait...");

	        // Run on a background thread - update date first, then guests
	        new Thread(() -> {
	            boolean dateSuccess = false;
	            boolean guestsSuccess = false;
	            
	            // Update date
	            ChatClient.ResetServerString();
	            ClientUI.chat.accept(updateDateHashMap);
	            while (ChatClient.fromserverString.equals(new String())) {
	                try {
	                    Thread.sleep(100);
	                } catch (InterruptedException e) {
	                    e.printStackTrace();
	                    break;
	                }
	            }
	            String dateResponse = ChatClient.fromserverString;
	            dateSuccess = "Updated".equals(dateResponse);
	            ChatClient.ResetServerString();
	            final String finalDateResponse = dateResponse;
	            
	            // Update number of guests
	            ChatClient.ResetServerString();
	            ClientUI.chat.accept(updateGuestsHashMap);
	            while (ChatClient.fromserverString.equals(new String())) {
	                try {
	                    Thread.sleep(100);
	                } catch (InterruptedException e) {
	                    e.printStackTrace();
	                    break;
	                }
	            }
	            guestsSuccess = "Updated".equals(ChatClient.fromserverString);
	            ChatClient.ResetServerString();

	            // Update UI on JavaFX thread
	            final boolean finalDateSuccess = dateSuccess;
	            final boolean finalGuestsSuccess = guestsSuccess;
	            final LocalDateTime finalNewDateTime = newDateTime;
	            Platform.runLater(() -> {
	                if (finalDateSuccess && finalGuestsSuccess) {
	                    lblStatus.setText("Updated successfully.");
	                    lblErr.setText("");
	                    // Update originalDateTime to reflect the change
	                    originalDateTime = finalNewDateTime;
	                } else {
	                	// Handle errors with alerts
	                	if (!finalDateSuccess) {
	                		if (finalDateResponse != null && finalDateResponse.startsWith("NO_CAPACITY:")) {
	                			// Parse alternative times from response
	                			String altTimesPart = finalDateResponse.substring("NO_CAPACITY:".length());
	                			String message = "Unfortunately, there is no available space at the requested time for " + numberOfGuests + " guests.\n\n";
	                			
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
	                			
	                			showAlert(Alert.AlertType.WARNING, "No Available Space", "Reservation Update Not Available", message);
	                		} else {
	                			showError("Failed to update reservation date.\n\nPlease try again or contact support if the problem persists.");
	                		}
	                	}
	                	if (!finalGuestsSuccess) {
	                		showError("Failed to update number of guests.\n\nThe restaurant may not have capacity for " + numberOfGuests + " guests at the selected time.\n\nPlease try a different number of guests or a different time.");
	                	}
	                	
	                	// Clear status labels
	                	lblStatus.setText("");
	                	lblErr.setText("");
	                }
	            });
	        }).start();
	    } catch (DateTimeParseException e) {
	        showError("Invalid time format.\n\nPlease select a valid time from the dropdown menu.");
	        e.printStackTrace();
	    } catch (Exception e) {
	        showError("Error processing update: " + e.getMessage() + "\n\nPlease try again or contact support if the problem persists.");
	        e.printStackTrace();
	    }
	}

	/**
	 * Handles the Cancel Reservation button click.
	 * Cancels the reservation by updating its status to "cancelled" in the database.
	 * 
	 * @param event - the click on the cancel button.
	 */
	public void CancelBtn(ActionEvent event) {
		String confirmationCode = txtConfirm.getText().trim();
		
		if (confirmationCode.isEmpty()) {
			showError("Please enter a confirmation code.");
			return;
		}
		
		// Confirm cancellation with user
		Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
		confirmAlert.setTitle("Cancel Reservation");
		confirmAlert.setHeaderText("Confirm Cancellation");
		confirmAlert.setContentText("Are you sure you want to cancel this reservation?");
		
		confirmAlert.showAndWait().ifPresent(response -> {
			if (response == javafx.scene.control.ButtonType.OK) {
				// User confirmed cancellation
				HashMap<String, String> cancelHashMap = new HashMap<>();
				cancelHashMap.put("CancelReservation", confirmationCode);
				
				lblStatus.setText("Cancelling reservation...\nPlease wait...");
				lblErr.setText("");
				
				// Run on a background thread
				new Thread(() -> {
					ChatClient.ResetServerString();
					ClientUI.chat.accept(cancelHashMap);
					
					// Wait for server response
					while (ChatClient.fromserverString.equals(new String())) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
							break;
						}
					}
					
					String responseStr = ChatClient.fromserverString;
					ChatClient.ResetServerString();
					
					// Update UI on JavaFX thread
					Platform.runLater(() -> {
						if ("Cancelled".equals(responseStr)) {
							showInfo("Reservation Cancelled", "Success", "Your reservation has been cancelled successfully.");
							// Clear the form
							datePicker.setValue(null);
							timeComboBox.setValue(null);
							txtGuests.clear();
							lblStatus.setText("");
							lblErr.setText("");
							loaded = 0;
						} else {
							showError("Failed to cancel reservation: " + responseStr + "\n\nPlease try again or contact support if the problem persists.");
							lblStatus.setText("");
							lblErr.setText("");
						}
					});
				}).start();
			}
		});
	}
	
	/**
	 * This method is for the load button. getting the string from the
	 * server and calling other method "LoadDetails()" to handle it and load into the GUI
	 * FIX: Changed to use confirmation_code instead of order_number
	 * 
	 * @param event - the click on the load button.
	 */
	public void Loadbtn(ActionEvent event) {
		String confirmationCode = txtConfirm.getText().trim();
		// FIX: Allow alphanumeric confirmation codes (not just digits)
		if (confirmationCode.isEmpty()) {
			showError("Please enter a confirmation code.");
			return;
		}
		
		// FIX: Run server communication on background thread to avoid blocking UI
		lblStatus.setText("Loading reservation...\nPlease wait...");
		lblErr.setText("");
		
		new Thread(() -> {
			HashMap<String, String> loadthisid = new HashMap<String, String>();
			loadthisid.put("LoadOrders", confirmationCode);
			
			// Reset response string before sending request
			ChatClient.ResetServerString();
			ClientUI.chat.accept(loadthisid);
			
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
			final String response = ChatClient.fromserverString;
			ChatClient.ResetServerString();
			
			Platform.runLater(() -> {
				if (response == null || response.isEmpty() || !response.contains(",") || response.equals("Empty")) {
					showError("Invalid confirmation code.\n\nCannot load the requested confirmation code. Please make sure you entered the correct code and try again.");
					lblErr.setText("");
					lblStatus.setText("");
				} else {
					// Check if reservation status is PENDING
					String[] parts = response.split(", ");
					if (parts.length >= 9) {
						String status = parts[8].trim(); // Status is the 9th element (index 8)
						if (!status.equalsIgnoreCase("PENDING")) {
							showError("Cannot edit reservation.\n\nThe reservation status is '" + status + "'.\n\nOnly reservations with status 'PENDING' can be edited.");
							lblErr.setText("");
							lblStatus.setText("");
							return;
						}
					}
					
					// No alert if everything is OK - just load the details
					lblErr.setText("");
					lblStatus.setText("");
					loaded = 1;
					LoadDetails(response);
				}
			});
		}).start();
	}

	/** This method is getting a string of subscriber and loading that data to the GUI
	 * enabling the only fields that we wants that the user will update.
	 * @param orderDetails
	 */
	public void LoadDetails(String orderDetails) {

	    String[] parts = orderDetails.split(", ");

	    // Parse and store original date+time
	    String dateStr = parts[1]; // order_date as Timestamp string
	    try {
	        originalDateTime = Timestamp.valueOf(dateStr).toLocalDateTime();
	    } catch (Exception e) {
	        // Fallback: try to parse manually if Timestamp.valueOf fails
	        try {
	            originalDateTime = LocalDateTime.parse(dateStr.replace(" ", "T"));
	        } catch (Exception e2) {
	            originalDateTime = LocalDateTime.now(); // Fallback to current time
	        }
	    }
	    
	    // Set DatePicker with original date
	    datePicker.setValue(originalDateTime.toLocalDate());
	    
	    // Set TimeComboBox with original time (format HH:mm)
	    String formattedTime = originalDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
	    timeComboBox.setValue(formattedTime);
	    
	    this.txtGuests.setText(parts[2]);           // number_of_guests
	    
	    // Fields are already editable through DatePicker, ComboBox, and TextField
	    txtConfirm.setEditable(false); // Make confirmation code input read-only after loading
	    
	    // Set minimum date to today (cannot select past dates)
	    datePicker.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
	        @Override
	        public void updateItem(LocalDate date, boolean empty) {
	            super.updateItem(date, empty);
	            if (date.isBefore(LocalDate.now())) {
	                setDisable(true);
	                setStyle("-fx-background-color: #ffcccc;");
	            }
	        }
	    });
	}
	
	/**
	 * Show error alert dialog with formatted message
	 * @param title Alert title
	 * @param header Alert header text
	 * @param msg Error message to display
	 */
	private void showAlert(Alert.AlertType alertType, String title, String header, String msg) {
		Alert alert = new Alert(alertType);
		alert.setTitle(title);
		alert.setHeaderText(header);
		
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
	
	/**
	 * Show error alert dialog with formatted message (convenience method)
	 * @param msg Error message to display
	 */
	private void showError(String msg) {
		showAlert(Alert.AlertType.ERROR, "Error", "Error", msg);
	}
	
	/**
	 * Show information alert dialog
	 * @param title Alert title
	 * @param header Alert header text
	 * @param msg Message to display
	 */
	private void showInfo(String title, String header, String msg) {
		showAlert(Alert.AlertType.INFORMATION, title, header, msg);
	}

	/**
	 * Format alternative time from database timestamp to user-friendly format
	 * @param timestampStr Timestamp string from database
	 * @return Formatted time string (date and time)
	 */
	private String formatAlternativeTime(String timestampStr) {
		try {
			// Parse the timestamp string (format: yyyy-MM-dd HH:mm:ss.0 or yyyy-MM-dd HH:mm:ss)
			String timestampToParse = timestampStr;
			int dotIndex = timestampStr.indexOf('.');
			if (dotIndex > 0) {
				timestampToParse = timestampStr.substring(0, dotIndex);
			}
			LocalDateTime dateTime = LocalDateTime.parse(timestampToParse, 
					DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
			return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
		} catch (Exception e) {
			// If parsing fails, return the original string
			return timestampStr;
		}
	}

}