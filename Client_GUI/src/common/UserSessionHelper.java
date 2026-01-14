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
    
    /**
     * Sets the user as a guest.
     */
    public static void setGuest() {
        currentUserType = UserType.GUEST;
        subscriberID = null;
        staffID = null;
        managerID = null;
    }
    
    /**
     * Sets the user as a subscriber.
     * 
     * @param subid The subscriber ID
     */
    public static void setSubscriber(String subid) {
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
     * Resets the session.
     */
    public static void reset() {
        currentUserType = UserType.GUEST;
        subscriberID = null;
        staffID = null;
        managerID = null;
    }
    
    /**
     * Navigates back to the appropriate menu screen based on the current user type.
     * If user is manager, navigates to ManagerMenu.
     * If user is staff, navigates to StaffMenu.
     * If user is subscriber, navigates to SubMenu.
     * If user is guest, navigates to GuestMenu.
     * 
     * @param eventSource The node that triggered the navigation (usually from ActionEvent.getSource())
     * @throws IOException if navigation fails
     */
    public static void navigateBackToMenu(Node eventSource) throws IOException {
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
        Scene scene = new Scene(root);
        scene.getStylesheets().add(UserSessionHelper.class.getResource(menuCss).toExternalForm());
        currentStage.setTitle(menuTitle);
        currentStage.setScene(scene);
    }
}

