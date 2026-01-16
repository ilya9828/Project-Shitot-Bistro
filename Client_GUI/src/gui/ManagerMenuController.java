package gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

/**
 * Controller for Manager Menu screen.
 * Manager has the same options as Staff, plus exclusive manager-only options.
 * This class extends StaffMenuController to inherit all functionality.
 */
public class ManagerMenuController extends StaffMenuController {
	
	@FXML
	private Button delayChartReportButton;
	@FXML
	private Button reservationChartInfoButton;
	
	/**
	 * Handles Delay Chart Report button click (Manager exclusive).
	 */
	@FXML
	private void handleDelayChartReport() {
		try {
			FXMLLoader loader = new FXMLLoader();
			
			// Get current window (don't hide it)
			Stage currentStage = (Stage) delayChartReportButton.getScene().getWindow();
			
			Parent root = loader.load(getClass().getResource("/gui/DelayChartReport.fxml").openStream());
			
			// Controller will request data itself in initialize() method
			// No need to pre-load data here
			
			Scene scene = new Scene(root);
			if (getClass().getResource("/gui/DelayChartReport.css") != null) {
				scene.getStylesheets().add(getClass().getResource("/gui/DelayChartReport.css").toExternalForm());
			}
			currentStage.setTitle("Delay Chart Report");
			currentStage.setScene(scene);
		} catch (Exception e) {
			System.err.println("Failed to load DelayChartReport screen: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Handles Reservation Chart Report button click (Manager exclusive).
	 */
	@FXML
	private void handleReservationChartInfo() {
		try {
			FXMLLoader loader = new FXMLLoader();
			
			// Get current window (don't hide it)
			Stage currentStage = (Stage) reservationChartInfoButton.getScene().getWindow();
			
			Parent root = loader.load(getClass().getResource("/gui/ReservationChartReport.fxml").openStream());
			
			// Controller will request data itself in initialize() method
			
			Scene scene = new Scene(root);
			if (getClass().getResource("/gui/ReservationChartReport.css") != null) {
				scene.getStylesheets().add(getClass().getResource("/gui/ReservationChartReport.css").toExternalForm());
			}
			currentStage.setTitle("Reservation Chart Report");
			currentStage.setScene(scene);
		} catch (Exception e) {
			System.err.println("Failed to load ReservationChartReport screen: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

