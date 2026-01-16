package common;

import java.io.IOException;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Helper class to track the current user session.
 * Stores whether the user is a guest, subscriber, staff, or manager.
 * Also provides navigation methods to return to the appropriate menu.
 */
public class UserSessionHelper {
    
    public enum UserType {
        GUEST,
        SUBSCRIBER,
        STAFF,
        MANAGER
    }
    
    private static UserType currentUserType = UserType.GUEST;
    private static String subscriberID = null;
    private static String staffID = null;
    private static String managerID = null;
    
    // Store original context when temporarily switching (e.g., for GuestOptions/SubscriberOptions)
    private static UserType originalUserType = null;
    private static String originalStaffID = null;
    private static String originalManagerID = null;
    private static String originalSubscriberID = null;
    
    /**
     * Sets the user as a guest.
     * If there's an original context stored, preserves it for restoration.
     */
    public static void setGuest() {
        // Store original context if we're switching from STAFF/MANAGER
        if (originalUserType == null && (currentUserType == UserType.STAFF || currentUserType == UserType.MANAGER)) {
            originalUserType = currentUserType;
            originalStaffID = staffID;
            originalManagerID = managerID;
            originalSubscriberID = subscriberID;
        }
        currentUserType = UserType.GUEST;
        subscriberID = null;
        staffID = null;
        managerID = null;
    }
    
    /**
     * Sets the user as a subscriber.
     * If there's an original context stored, preserves it for restoration.
     * 
     * @param subid The subscriber ID
     */
    public static void setSubscriber(String subid) {
        // Store original context if we're switching from STAFF/MANAGER
        if (originalUserType == null && (currentUserType == UserType.STAFF || currentUserType == UserType.MANAGER)) {
            originalUserType = currentUserType;
            originalStaffID = staffID;
            originalManagerID = managerID;
            originalSubscriberID = subscriberID;
        }
        currentUserType = UserType.SUBSCRIBER;
        subscriberID = subid;
        staffID = null;
        managerID = null;
    }
    
    /**
     * Sets the user as staff.
     * 
     * @param staffId The staff ID
     */
    public static void setStaff(String staffId) {
        // Only store original context if we're switching FROM staff/manager TO guest/subscriber
        // Don't store if we're restoring (originalUserType is already set)
        if (originalUserType == null && (currentUserType == UserType.STAFF || currentUserType == UserType.MANAGER)) {
            // This is a normal set, not a restore
        }
        currentUserType = UserType.STAFF;
        staffID = staffId;
        subscriberID = null;
        managerID = null;
    }
    
    /**
     * Sets the user as manager.
     * 
     * @param mgrId The manager ID
     */
    public static void setManager(String mgrId) {
        // Only store original context if we're switching FROM staff/manager TO guest/subscriber
        // Don't store if we're restoring (originalUserType is already set)
        if (originalUserType == null && (currentUserType == UserType.STAFF || currentUserType == UserType.MANAGER)) {
            // This is a normal set, not a restore
        }
        currentUserType = UserType.MANAGER;
        managerID = mgrId;
        subscriberID = null;
        staffID = null;
    }
    
    /**
     * Gets the current user type.
     * 
     * @return UserType (GUEST, SUBSCRIBER, STAFF, or MANAGER)
     */
    public static UserType getCurrentUserType() {
        return currentUserType;
    }
    
    /**
     * Gets the subscriber ID if the user is a subscriber.
     * 
     * @return Subscriber ID or null if not subscriber
     */
    public static String getSubscriberID() {
        return subscriberID;
    }
    
    /**
     * Gets the staff ID if the user is staff.
     * 
     * @return Staff ID or null if not staff
     */
    public static String getStaffID() {
        return staffID;
    }
    
    /**
     * Gets the manager ID if the user is a manager.
     * 
     * @return Manager ID or null if not manager
     */
    public static String getManagerID() {
        return managerID;
    }
    
    /**
     * Checks if the current user is a guest.
     * 
     * @return true if guest, false otherwise
     */
    public static boolean isGuest() {
        return currentUserType == UserType.GUEST;
    }
    
    /**
     * Checks if the current user is a subscriber.
     * 
     * @return true if subscriber, false otherwise
     */
    public static boolean isSubscriber() {
        return currentUserType == UserType.SUBSCRIBER;
    }
    
    /**
     * Checks if the current user is staff.
     * 
     * @return true if staff, false otherwise
     */
    public static boolean isStaff() {
        return currentUserType == UserType.STAFF;
    }
    
    /**
     * Checks if the current user is a manager.
     * 
     * @return true if manager, false otherwise
     */
    public static boolean isManager() {
        return currentUserType == UserType.MANAGER;
    }
    
    /**
     * Checks if there's a stored original context (e.g., from Staff/Manager accessing Guest/Subscriber screens).
     * 
     * @return true if there's an original context stored, false otherwise
     */
    public static boolean hasOriginalContext() {
        return originalUserType != null;
    }
    
    /**
     * Resets the session.
     */
    public static void reset() {
        currentUserType = UserType.GUEST;
        subscriberID = null;
        staffID = null;
        managerID = null;
        // Also clear original context
        originalUserType = null;
        originalStaffID = null;
        originalManagerID = null;
        originalSubscriberID = null;
    }
    
    /**
     * Restores the original user context if it was temporarily changed.
     * This is used when navigating back from screens accessed via GuestOptions/SubscriberOptions.
     */
    public static void restoreOriginalContext() {
        if (originalUserType != null) {
            UserType restoreType = originalUserType;
            String restoreStaffID = originalStaffID;
            String restoreManagerID = originalManagerID;
            String restoreSubscriberID = originalSubscriberID;
            
            // Clear stored original context first to avoid recursion
            originalUserType = null;
            originalStaffID = null;
            originalManagerID = null;
            originalSubscriberID = null;
            
            // Now restore the context
            if (restoreType == UserType.STAFF && restoreStaffID != null) {
                currentUserType = UserType.STAFF;
                staffID = restoreStaffID;
                subscriberID = null;
                managerID = null;
            } else if (restoreType == UserType.MANAGER && restoreManagerID != null) {
                currentUserType = UserType.MANAGER;
                managerID = restoreManagerID;
                subscriberID = null;
                staffID = null;
            } else if (restoreType == UserType.SUBSCRIBER && restoreSubscriberID != null) {
                currentUserType = UserType.SUBSCRIBER;
                subscriberID = restoreSubscriberID;
                staffID = null;
                managerID = null;
            }
        }
    }
    
    /**
     * Navigates back to the appropriate menu screen based on the current user type.
     * 
     * If called from a screen (EditPersonalInfo, ShowHistory, CheckIn, etc.) and there's an original context:
     *   - Goes to intermediate menu (SubMenu/GuestMenu) WITHOUT restoring context
     * 
     * If called from a menu (SubMenu/GuestMenu) and there's an original context:
     *   - Restores original context and goes to StaffMenu/ManagerMenu
     * 
     * Otherwise:
     *   - Goes to the appropriate menu based on current user type
     * 
     * @param eventSource The node that triggered the navigation (usually from ActionEvent.getSource())
     * @param fromMenu If true, indicates this is called from a menu (SubMenu/GuestMenu), so restore context if exists
     * @throws IOException if navigation fails
     */
    public static void navigateBackToMenu(Node eventSource, boolean fromMenu) throws IOException {
        // If called from menu AND there's original context, restore it first
        if (fromMenu && originalUserType != null) {
            restoreOriginalContext();
        }
        
        // If there's original context and we're NOT coming from a menu, go to intermediate menu first
        if (!fromMenu && originalUserType != null) {
            // Go to intermediate menu (SubMenu or GuestMenu) based on current user type
            navigateToIntermediateMenu(eventSource);
            return;
        }
        
        // Otherwise, navigate to the appropriate menu based on current user type
        String menuFxml;
        String menuCss;
        String menuTitle;
        
        if (currentUserType == UserType.MANAGER) {
            menuFxml = "/gui/ManagerMenu.fxml";
            menuCss = "/gui/ManagerMenu.css";
            menuTitle = "Manager Menu";
        } else if (currentUserType == UserType.STAFF) {
            menuFxml = "/gui/StaffMenu.fxml";
            menuCss = "/gui/StaffMenu.css";
            menuTitle = "Staff Menu";
        } else if (currentUserType == UserType.SUBSCRIBER) {
            menuFxml = "/gui/SubMenu.fxml";
            menuCss = "/gui/SubMenu.css";
            menuTitle = "Subscriber Menu";
        } else { // GUEST
            menuFxml = "/gui/GuestMenu.fxml";
            menuCss = "/gui/GuestMenu.css";
            menuTitle = "Guest Menu";
        }
        
        // Get current window (don't create a new one)
        Stage currentStage = (Stage) eventSource.getScene().getWindow();
        
        FXMLLoader loader = new FXMLLoader(UserSessionHelper.class.getResource(menuFxml));
        Parent root = loader.load();
        
        // If subscriber, set the subscriber ID in the controller
        if (currentUserType == UserType.SUBSCRIBER && subscriberID != null) {
            try {
                Object controller = loader.getController();
                if (controller instanceof gui.SubMenuController) {
                    ((gui.SubMenuController) controller).setSubscriberID(subscriberID);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        Scene scene = new Scene(root);
        scene.getStylesheets().add(UserSessionHelper.class.getResource(menuCss).toExternalForm());
        currentStage.setTitle(menuTitle);
        currentStage.setScene(scene);
    }
    
    /**
     * Overloaded method for backward compatibility.
     * Assumes called from a screen (not a menu), so goes to intermediate menu if original context exists.
     */
    public static void navigateBackToMenu(Node eventSource) throws IOException {
        navigateBackToMenu(eventSource, false);
    }
    
    /**
     * Navigates to the intermediate menu (SubMenu or GuestMenu) without restoring context.
     * This is used when screens need to go back to the menu they came from.
     */
    private static void navigateToIntermediateMenu(Node eventSource) throws IOException {
        String menuFxml;
        String menuCss;
        String menuTitle;
        
        if (currentUserType == UserType.SUBSCRIBER) {
            menuFxml = "/gui/SubMenu.fxml";
            menuCss = "/gui/SubMenu.css";
            menuTitle = "Subscriber Menu";
        } else { // GUEST
            menuFxml = "/gui/GuestMenu.fxml";
            menuCss = "/gui/GuestMenu.css";
            menuTitle = "Guest Menu";
        }
        
        // Get current window (don't create a new one)
        Stage currentStage = (Stage) eventSource.getScene().getWindow();
        
        FXMLLoader loader = new FXMLLoader(UserSessionHelper.class.getResource(menuFxml));
        Parent root = loader.load();
        
        // If subscriber, set the subscriber ID in the controller
        if (currentUserType == UserType.SUBSCRIBER && subscriberID != null) {
            try {
                Object controller = loader.getController();
                if (controller instanceof gui.SubMenuController) {
                    ((gui.SubMenuController) controller).setSubscriberID(subscriberID);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        Scene scene = new Scene(root);
        scene.getStylesheets().add(UserSessionHelper.class.getResource(menuCss).toExternalForm());
        currentStage.setTitle(menuTitle);
        currentStage.setScene(scene);
    }
}

