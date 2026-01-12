package gui;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * This class controls the Edit Opening Hours screen.
 * Allows staff to edit restaurant opening hours for selected days.
 */
public class EditOpeningHoursController {

    @FXML
    private ComboBox<String> openingTimeComboBox;
    @FXML
    private ComboBox<String> closingTimeComboBox;

    @FXML
    private CheckBox specificDayCheckBox;
    @FXML
    private ComboBox<String> dayOfWeekComboBox;
    @FXML
    private DatePicker specificDatePicker;
    @FXML
    private CheckBox numberOfDaysCheckBox;
    @FXML
    private TextField numberOfDaysTextField;
    @FXML
    private CheckBox permanentCheckBox;
    @FXML
    private VBox permanentDaysContainer;
    @FXML
    private CheckBox permSundayCheckBox;
    @FXML
    private CheckBox permMondayCheckBox;
    @FXML
    private CheckBox permTuesdayCheckBox;
    @FXML
    private CheckBox permWednesdayCheckBox;
    @FXML
    private CheckBox permThursdayCheckBox;
    @FXML
    private CheckBox permFridayCheckBox;
    @FXML
    private CheckBox permSaturdayCheckBox;
    @FXML
    private CheckBox permWholeWeekCheckBox;

    @FXML
    private Button saveButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Button btnBack;
    @FXML
    private Button btnExit;

    /**
     * Initialize the controller - set up time combos and listeners
     */
    @FXML
    public void initialize() {
        // Initialize time combo boxes
        initTimeComboBoxes();
        
        // Initialize day of week combo box (Sunday first)
        initDayOfWeekComboBox();
        
        // Initially disable the input fields
        dayOfWeekComboBox.setDisable(true);
        specificDatePicker.setDisable(true);
        numberOfDaysTextField.setDisable(true);
        
        // Enable/disable fields based on specific day checkbox
        specificDayCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            // NOT selected = disabled fields, selected = enabled fields.
            dayOfWeekComboBox.setDisable(!newValue);
            specificDatePicker.setDisable(!newValue);
            if (newValue) {
                numberOfDaysCheckBox.setSelected(false);
                numberOfDaysTextField.setDisable(true);
                permanentCheckBox.setSelected(false);
            }
        });
        
        // Enable/disable number of days text field based on checkbox
        numberOfDaysCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            numberOfDaysTextField.setDisable(!newValue);
            if (newValue) {
                specificDayCheckBox.setSelected(false);
                dayOfWeekComboBox.setDisable(true);
                specificDatePicker.setDisable(true);
                permanentCheckBox.setSelected(false);
            }
        });
        
        // Enable/disable permanent days checkboxes based on permanent checkbox
        permanentCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (permanentDaysContainer != null) {
                permanentDaysContainer.setDisable(!newValue);
            }
            if (newValue) {
                specificDayCheckBox.setSelected(false);
                dayOfWeekComboBox.setDisable(true);
                specificDatePicker.setDisable(true);
                numberOfDaysCheckBox.setSelected(false);
                numberOfDaysTextField.setDisable(true);
            }
        });
        
        // Handle "Whole Week" checkbox - when checked, select all days; when unchecked, unselect all
        if (permWholeWeekCheckBox != null) {
            permWholeWeekCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    // Select all day checkboxes
                    if (permSundayCheckBox != null) permSundayCheckBox.setSelected(true);
                    if (permMondayCheckBox != null) permMondayCheckBox.setSelected(true);
                    if (permTuesdayCheckBox != null) permTuesdayCheckBox.setSelected(true);
                    if (permWednesdayCheckBox != null) permWednesdayCheckBox.setSelected(true);
                    if (permThursdayCheckBox != null) permThursdayCheckBox.setSelected(true);
                    if (permFridayCheckBox != null) permFridayCheckBox.setSelected(true);
                    if (permSaturdayCheckBox != null) permSaturdayCheckBox.setSelected(true);
                } else {
                    // Unselect all day checkboxes
                    if (permSundayCheckBox != null) permSundayCheckBox.setSelected(false);
                    if (permMondayCheckBox != null) permMondayCheckBox.setSelected(false);
                    if (permTuesdayCheckBox != null) permTuesdayCheckBox.setSelected(false);
                    if (permWednesdayCheckBox != null) permWednesdayCheckBox.setSelected(false);
                    if (permThursdayCheckBox != null) permThursdayCheckBox.setSelected(false);
                    if (permFridayCheckBox != null) permFridayCheckBox.setSelected(false);
                    if (permSaturdayCheckBox != null) permSaturdayCheckBox.setSelected(false);
                }
            });
            
            // When any individual day is unchecked, uncheck "Whole Week"
            java.util.function.Consumer<Boolean> updateWholeWeek = (isSelected) -> {
                if (!isSelected && permWholeWeekCheckBox.isSelected()) {
                    permWholeWeekCheckBox.setSelected(false);
                }
            };
            
            if (permSundayCheckBox != null) {
                permSundayCheckBox.selectedProperty().addListener((obs, old, val) -> updateWholeWeek.accept(val));
            }
            if (permMondayCheckBox != null) {
                permMondayCheckBox.selectedProperty().addListener((obs, old, val) -> updateWholeWeek.accept(val));
            }
            if (permTuesdayCheckBox != null) {
                permTuesdayCheckBox.selectedProperty().addListener((obs, old, val) -> updateWholeWeek.accept(val));
            }
            if (permWednesdayCheckBox != null) {
                permWednesdayCheckBox.selectedProperty().addListener((obs, old, val) -> updateWholeWeek.accept(val));
            }
            if (permThursdayCheckBox != null) {
                permThursdayCheckBox.selectedProperty().addListener((obs, old, val) -> updateWholeWeek.accept(val));
            }
            if (permFridayCheckBox != null) {
                permFridayCheckBox.selectedProperty().addListener((obs, old, val) -> updateWholeWeek.accept(val));
            }
            if (permSaturdayCheckBox != null) {
                permSaturdayCheckBox.selectedProperty().addListener((obs, old, val) -> updateWholeWeek.accept(val));
            }
        }
    }
    
    /**
     * Initialize day of week combo box with days (Sunday first)
     */
    private void initDayOfWeekComboBox() {
        dayOfWeekComboBox.getItems().addAll(
            "Sunday", "Monday", "Tuesday", "Wednesday", 
            "Thursday", "Friday", "Saturday"
        );
    }

    /**
     * Initialize time combo boxes with time options (30-minute intervals from 00:00 to 23:30)
     */
    private void initTimeComboBoxes() {
        if (openingTimeComboBox == null || closingTimeComboBox == null) {
            return;
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        List<String> timeOptions = new ArrayList<>();
        
        // creates time stamps for opening and closing times, from 00:00 to 23:30 in 30-minute intervals
        for (int hour = 0; hour < 24; hour++) {
            for (int minute = 0; minute < 60; minute += 30) {
                LocalTime time = LocalTime.of(hour, minute);
                timeOptions.add(time.format(formatter));
            }
        }

        openingTimeComboBox.getItems().addAll(timeOptions);
        closingTimeComboBox.getItems().addAll(timeOptions);
    }

    /**
     * Handle the Save button click - validates and saves opening hours
     */
    @FXML
    private void handleSave() {
        // Get opening and closing times
        String openingTime = openingTimeComboBox.getValue();
        String closingTime = closingTimeComboBox.getValue();

        if (openingTime == null || closingTime == null) {
            statusLabel.setText("Please select both opening and closing times.");
            showError("Please select both opening and closing times.");
            return;
        }

        // Validate times
        if (openingTime.compareTo(closingTime) >= 0) {
            statusLabel.setText("Opening time must be before closing time.");
            showError("Opening time must be before closing time.");
            return;
        }

        // Get duration type
        boolean isSpecificDay = specificDayCheckBox.isSelected();
        boolean isNumberOfDays = numberOfDaysCheckBox.isSelected();
        boolean isPermanent = permanentCheckBox.isSelected();
        String durationType = "";
        String durationValue = "";

        if (isSpecificDay) {
            String dayOfWeek = dayOfWeekComboBox.getValue();
            LocalDate selectedDate = specificDatePicker.getValue();
            if (dayOfWeek == null || selectedDate == null) {
                statusLabel.setText("Please select both day of week and date for specific day.");
                showError("Please select both day of week and date for specific day.");
                return;
            }
            
            // Validate that the selected date matches the selected day of week
            String dateDayOfWeek = selectedDate.getDayOfWeek().toString();
            // Convert Java DayOfWeek to our format (e.g., MONDAY -> Monday)
            String dateDayFormatted = dateDayOfWeek.charAt(0) + dateDayOfWeek.substring(1).toLowerCase();
            
            if (!dateDayFormatted.equalsIgnoreCase(dayOfWeek)) {
                statusLabel.setText("The selected date is not a " + dayOfWeek + ". Please select a " + dayOfWeek + " date.");
                showError("The selected date (" + selectedDate.toString() + ") is a " + dateDayFormatted + ", but you selected " + dayOfWeek + ".\nPlease select a date that falls on " + dayOfWeek + ".");
                return;
            }
            
            durationType = "SPECIFIC_DAY";
            durationValue = dayOfWeek + "|" + selectedDate.toString();
        } else if (isNumberOfDays) {
            String daysText = numberOfDaysTextField.getText().trim();
            if (daysText.isEmpty()) {
                statusLabel.setText("Please enter the number of days.");
                showError("Please enter the number of days.");
                return;
            }
            try {
                int numberOfDays = Integer.parseInt(daysText);
                if (numberOfDays <= 0) {
                    statusLabel.setText("Number of days must be greater than 0.");
                    showError("Number of days must be greater than 0.");
                    return;
                }
                durationType = "NUMBER_OF_DAYS";
                durationValue = String.valueOf(numberOfDays);
            } catch (NumberFormatException e) {
                statusLabel.setText("Please enter a valid number for days.");
                showError("Please enter a valid number for days.");
                return;
            }
        } else if (isPermanent) {
            // Get selected days for permanent update
            List<String> selectedDays = getSelectedPermanentDays();
            if (selectedDays.isEmpty()) {
                statusLabel.setText("Please select at least one day for permanent hours.");
                showError("Please select at least one day for permanent hours.");
                return;
            }
            durationType = "PERMANENT";
            durationValue = String.join(",", selectedDays); // Comma-separated list of days
        } else {
            statusLabel.setText("Please select a duration option.");
            showError("Please select a duration option (Specific Day, Number of Days, or Permanently).");
            return;
        }

        statusLabel.setText("Saving opening hours... Please wait...");

        // Create request to send to server
        // Format: openingTime|closingTime|durationType|durationValue
        String data = openingTime + "|" + closingTime + "|" + durationType + "|" + durationValue;

        HashMap<String, String> openingHoursData = new HashMap<>();
        openingHoursData.put("EditOpeningHours", data);

        // Send to server (this blocks until response is received)
        ClientUI.chat.accept(openingHoursData);

        // Check the response
        String response = ChatClient.fromserverString;
        if ("OpeningHoursUpdated".equals(response)) {
            statusLabel.setText("");
            showInfo("Opening hours updated successfully!");
            clearFields();
        } else {
            statusLabel.setText("");
            String errorMsg = "Failed to update opening hours. ";
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
     * Get list of selected days for permanent hours
     */
    private List<String> getSelectedPermanentDays() {
        List<String> selectedDays = new ArrayList<>();
        if (permSundayCheckBox != null && permSundayCheckBox.isSelected()) selectedDays.add("Sunday");
        if (permMondayCheckBox != null && permMondayCheckBox.isSelected()) selectedDays.add("Monday");
        if (permTuesdayCheckBox != null && permTuesdayCheckBox.isSelected()) selectedDays.add("Tuesday");
        if (permWednesdayCheckBox != null && permWednesdayCheckBox.isSelected()) selectedDays.add("Wednesday");
        if (permThursdayCheckBox != null && permThursdayCheckBox.isSelected()) selectedDays.add("Thursday");
        if (permFridayCheckBox != null && permFridayCheckBox.isSelected()) selectedDays.add("Friday");
        if (permSaturdayCheckBox != null && permSaturdayCheckBox.isSelected()) selectedDays.add("Saturday");
        return selectedDays;
    }

    /**
     * Clear all input fields after successful save
     */
    private void clearFields() {
        openingTimeComboBox.setValue(null);
        closingTimeComboBox.setValue(null);
        specificDayCheckBox.setSelected(false);
        dayOfWeekComboBox.setValue(null);
        dayOfWeekComboBox.setDisable(true);
        specificDatePicker.setValue(null);
        specificDatePicker.setDisable(true);
        numberOfDaysCheckBox.setSelected(false);
        numberOfDaysTextField.setText("");
        numberOfDaysTextField.setDisable(true);
        permanentCheckBox.setSelected(false);
        permanentDaysContainer.setDisable(true);
        if (permSundayCheckBox != null) permSundayCheckBox.setSelected(false);
        if (permMondayCheckBox != null) permMondayCheckBox.setSelected(false);
        if (permTuesdayCheckBox != null) permTuesdayCheckBox.setSelected(false);
        if (permWednesdayCheckBox != null) permWednesdayCheckBox.setSelected(false);
        if (permThursdayCheckBox != null) permThursdayCheckBox.setSelected(false);
        if (permFridayCheckBox != null) permFridayCheckBox.setSelected(false);
        if (permSaturdayCheckBox != null) permSaturdayCheckBox.setSelected(false);
        if (permWholeWeekCheckBox != null) permWholeWeekCheckBox.setSelected(false);
    }

    /**
     * This method is for the back button closing the current GUI and uploading the menu GUI.
     * @param event - click on the back button.
     * @throws IOException
     */
    public void Back(ActionEvent event) throws IOException {
        UserSessionHelper.navigateBackToMenu((Node) event.getSource());
    }

    /**
     * This method is for the exit button sending a message to the server that now we are disconnecting,
     * closing the GUI and the connection for the server.
     */
    public void getExitBtn(ActionEvent event) throws Exception {
        System.out.println("Disconnecting from the Server and ending the program.");
        HashMap<String, String> EndingConnections = new HashMap<String, String>();
        EndingConnections.put("Disconnect", "");
        ClientUI.chat.accept(EndingConnections);
        System.exit(0);
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

