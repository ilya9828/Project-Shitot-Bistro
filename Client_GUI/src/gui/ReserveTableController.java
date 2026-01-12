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
import entities.Reservations;
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
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

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
    private Button reserveButton;
    @FXML
    Labeled lblStatus = null;
    @FXML
    private Button btnExit = null;
    @FXML
    private Button btnBack = null;

    @FXML
    public void initialize() {
        System.out.println("ReserveTableController initialized");

        initTimeComboBox();
    }

    private void initTimeComboBox() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        LocalTime time = LocalTime.of(12, 0);
        LocalTime end = LocalTime.of(22, 0);

        while (!time.isAfter(end)) {
            timeComboBox.getItems().add(time.format(formatter));
            time = time.plusMinutes(30);
        }
        

        System.out.println("Time options loaded: " + timeComboBox.getItems().size());
    }
    
    /*
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
	
	/** This method is for the back button closing the current GUI and uploading the menu GUI.
     * @param event - click on the back button.
     * @throws IOException
     */
    public void Back(ActionEvent event) throws IOException {
        FXMLLoader ordersLoader = new FXMLLoader(getClass().getResource("/gui/Menu.fxml"));
        Parent ordersRoot = ordersLoader.load();
        Stage ordersStage = new Stage();
        Scene ordersScene = new Scene(ordersRoot);
        ordersScene.getStylesheets().add(getClass().getResource("/gui/Menu.css").toExternalForm());
        ordersStage.setScene(ordersScene);
        ordersStage.setTitle("Menu");
        ordersStage.show();
        ((Node) event.getSource()).getScene().getWindow().hide();
    }
    
    

    @FXML
    private void handleReserve() {

        LocalDate reservationDate = datePicker.getValue();
        String time = timeComboBox.getValue();
        String guestsText = guestsField.getText();
        String inputText = input.getText(); // השדה שמקבל קלט
        String email = "";
        String phoneNumber = "";
        String nameText = name.getText();
        
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
        }


		if (reservationDate == null || time == null ||
            guestsText.isEmpty() || inputText.isEmpty()) {
            lblStatus.setText("Please fill all fields.");
            return;
        }

        int numberOfGuests;
        try {
            numberOfGuests = Integer.parseInt(guestsText);
        } catch (NumberFormatException e) {
            lblStatus.setText("Guests must be a number.");
            return;
        }

        // ===== יצירת נתונים =====
        Integer subscriberId = null;
        boolean isSubscriber = false;

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
                isSubscriber,
                email,
                phoneNumber,
                nameText
                
        );

        lblStatus.setText("Sending reservation to server...\nPlease wait...");

        new Thread(() -> {

            ClientUI.chat.accept(reservation);

            Platform.runLater(() -> {
                if ("ReservationAdded".equals(ChatClient.fromserverString)) {
                    lblStatus.setText("");
                	showInfo(
                            "Reservation created successfully!\n" +
                            "Confirmation Code: " + confirmationCode
                    );
                } else {
                    lblStatus.setText("Failed to create reservation.");
                }

                ChatClient.ResetServerString();
            });

        }).start();
    }

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
