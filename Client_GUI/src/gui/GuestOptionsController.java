package gui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

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
     * Closes the current window and shows the previous Manager/Staff menu.
     */
    @FXML
    private void handleBack() {
        if (backToLoginButton != null && backToLoginButton.getScene() != null) {
            Stage currentStage = (Stage) backToLoginButton.getScene().getWindow();
            
            // Get the position and size before closing
            double x = currentStage.getX();
            double y = currentStage.getY();
            double width = currentStage.getWidth();
            double height = currentStage.getHeight();
            
            currentStage.close();
            
            // Reopen the Manager/Staff menu at the same position
            try {
                // Determine which menu to reopen based on user session
                String menuFxml;
                String menuCss;
                String menuTitle;
                
                if (common.UserSessionHelper.isManager()) {
                    menuFxml = "/gui/ManagerMenu.fxml";
                    menuCss = "/gui/ManagerMenu.css";
                    menuTitle = "Manager Menu";
                } else if (common.UserSessionHelper.isStaff()) {
                    menuFxml = "/gui/StaffMenu.fxml";
                    menuCss = "/gui/StaffMenu.css";
                    menuTitle = "Staff Menu";
                } else {
                    return; // Not manager or staff, just close
                }
                
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource(menuFxml));
                javafx.scene.Parent root = loader.load();
                
                Stage stage = new Stage();
                javafx.scene.Scene scene = new javafx.scene.Scene(root, width, height);
                scene.getStylesheets().add(getClass().getResource(menuCss).toExternalForm());
                stage.setScene(scene);
                stage.setTitle(menuTitle);
                stage.setX(x);
                stage.setY(y);
                stage.setWidth(width);
                stage.setHeight(height);
                stage.setMinWidth(width);
                stage.setMinHeight(height);
                
                // Handle window close
                stage.setOnCloseRequest(closeEvent -> {
                    try {
                        if (client.ClientUI.chat != null) {
                            java.util.HashMap<String, String> disconnectMsg = new java.util.HashMap<>();
                            disconnectMsg.put("Disconnect", "");
                            client.ClientUI.chat.accept(disconnectMsg);
                            Thread.sleep(200);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                
                stage.show();
            } catch (Exception e) {
                System.err.println("Failed to reopen menu: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

