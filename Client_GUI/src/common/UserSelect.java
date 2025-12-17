package common;

/** This enum is as menu to user select options
 * 
 */
public enum UserSelect {
    ShowAllOrders("Show All Orders"),
    UpdateOrderDate("Update Order Date"),
    UpdateNumberOfGuests("Update Number Of Guests"),
	ReserveTable("Reserve Table");

    private final String displayName;

    // Constructor to set the display name for each enum
    UserSelect(String displayName) {
        this.displayName = displayName;
    }

    // Getter to retrieve the display name
    public String getDisplayName() {
        return this.displayName;
    }
    
    /** This method is geting the userselect if the string equals to the display name
     * @param select - string of the user select
     * @return UserSelect - that the user selected  / null if not found
     */
    public UserSelect getSelection(String select) {
        for (UserSelect action : UserSelect.values()) {
            if (action.getDisplayName().equalsIgnoreCase(select)) {
                return action;
            }
        }
        return null;
    }
    
    /** This method is returning the UserSelect enum if we got enum as a string (but not the display message)
     * if there is not enum like this we returning null.
     * @param enumName - the string of the exact enum (not displayname)
     * @return UserSelect
     */
    public static UserSelect getSelectionFromEnumName(String enumName) {
        for (UserSelect action : UserSelect.values()) {
            if (action.name().equalsIgnoreCase(enumName)) {  // Compare with enum name (e.g., "ShowAllSubscribers")
                return action;
            }
        }
        return null;  // Return null if no match is found
    }
}
