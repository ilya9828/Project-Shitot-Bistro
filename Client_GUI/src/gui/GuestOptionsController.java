package gui;

import java.io.IOException;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import common.UserSessionHelper;

/**
 * Controller for the Guest Options screen.
 * Provides access to guest functions: ReserveTable, EditReservation, CheckIn,
 * JoinWaitingList, ExitWaitingList, PayBill.
 * Inherits all common functionality from BaseMenuController.
 */
public class GuestOptionsController extends BaseMenuController {
    
    @FXML
    private Button backToLoginButton;
    
    /**
     * Handles the "Back" button click.
     * Navigates back to the Manager/Staff menu.
     */
    @FXML
    private void handleBack(ActionEvent event) {
        try {
            UserSessionHelper.navigateBackToMenu((Node) event.getSource());
        } catch (IOException e) {
            System.err.println("Failed to navigate back to menu: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

