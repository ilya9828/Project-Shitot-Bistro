package common;

import javafx.scene.control.Alert;

/**
 * Helper class to centralize alert dialog creation.
 * Provides static methods that can be used by all controllers, regardless of inheritance.
 * This eliminates duplicate alert code across multiple controllers.
 */
public class AlertHelper {
    
    /**
     * Shows an alert dialog with the specified type, title, and message.
     * 
     * @param alertType The type of alert (ERROR, INFORMATION, WARNING, etc.)
     * @param title The alert title
     * @param message The message to display
     */
    public static void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Shows an error alert dialog.
     * 
     * @param title The alert title
     * @param message The error message to display
     */
    public static void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }
    
    /**
     * Shows an info alert dialog.
     * 
     * @param title The alert title
     * @param message The info message to display
     */
    public static void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }
    
    /**
     * Shows a success alert dialog.
     * 
     * @param title The alert title
     * @param message The success message to display
     */
    public static void showSuccess(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }
    
    /**
     * Shows a warning alert dialog.
     * 
     * @param title The alert title
     * @param message The warning message to display
     */
    public static void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }
}
