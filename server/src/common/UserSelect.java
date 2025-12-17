package common;
/*
 * This enum is defining the action select of the user
 */
public enum UserSelect {
    ShowAllOrders("Show All Orders"),
    UpdateOrderDate("Update order Date"),
    UpdateNumberOfGuests("Update Number Of Guests"),
	LoadOrders("Load Orders"),
	Connected("Connected"),
	Disconnect("Disconnect");

    private final String displayName;

    /* 
     * Constructor to set the display name for each enum
     */
    UserSelect(String displayName) {
        this.displayName = displayName;
    }

    /* 
     * Getter to retrieve the display name
     */
    public String getDisplayName() {
        return this.displayName;
    }
    /*
     * Getter to retrieve the enum itself if there is his name.
     */
    public static UserSelect getSelectionFromEnumName(String enumName) {
        for (UserSelect action : UserSelect.values()) {
            if (action.name().equalsIgnoreCase(enumName)) {  // Compare with enum name
                return action;
            }
        }
        return null;
    }
}