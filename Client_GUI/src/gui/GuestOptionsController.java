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
     * Uses fromMenu=true to restore context if we came from Staff/Manager.
     */
    @FXML
    private void handleBack(ActionEvent event) {
        try {
            // fromMenu=true will restore context and go back to Staff/Manager if original context exists
            UserSessionHelper.navigateBackToMenu((Node) event.getSource(), true);
        } catch (IOException e) {
            System.err.println("Failed to navigate back to menu: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

