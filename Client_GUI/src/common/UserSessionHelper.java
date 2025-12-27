package common;

/**
 * Helper class to track the current user session.
 * Stores whether the user is a guest or subscriber.
 */
public class UserSessionHelper {
    
    public enum UserType {
        GUEST,
        SUBSCRIBER
    }
    
    private static UserType currentUserType = UserType.GUEST;
    private static String subscriberID = null;
    
    /**
     * Sets the user as a guest.
     */
    public static void setGuest() {
        currentUserType = UserType.GUEST;
        subscriberID = null;
    }
    
    /**
     * Sets the user as a subscriber.
     * 
     * @param subid The subscriber ID
     */
    public static void setSubscriber(String subid) {
        currentUserType = UserType.SUBSCRIBER;
        subscriberID = subid;
    }
    
    /**
     * Gets the current user type.
     * 
     * @return UserType (GUEST or SUBSCRIBER)
     */
    public static UserType getCurrentUserType() {
        return currentUserType;
    }
    
    /**
     * Gets the subscriber ID if the user is a subscriber.
     * 
     * @return Subscriber ID or null if guest
     */
    public static String getSubscriberID() {
        return subscriberID;
    }
    
    /**
     * Checks if the current user is a guest.
     * 
     * @return true if guest, false if subscriber
     */
    public static boolean isGuest() {
        return currentUserType == UserType.GUEST;
    }
    
    /**
     * Checks if the current user is a subscriber.
     * 
     * @return true if subscriber, false if guest
     */
    public static boolean isSubscriber() {
        return currentUserType == UserType.SUBSCRIBER;
    }
    
    /**
     * Resets the session.
     */
    public static void reset() {
        currentUserType = UserType.GUEST;
        subscriberID = null;
    }
}

