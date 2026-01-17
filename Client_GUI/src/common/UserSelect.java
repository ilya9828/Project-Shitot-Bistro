package common;

/**
 * Enumeration of all available user menu options/actions.
 * Provides a centralized way to identify different operations
 * that can be performed by clients on the server.
 * 
 * @author Dream Team 
 * @version 300.1.6
 */
public enum UserSelect {
    UpdateOrderDate("Update Order Date"),
    UpdateNumberOfGuests("Update Number Of Guests"),
	ReserveTable("Reserve Table"),
	CheckIn("Check In"),
	WaitingList("Waiting List"),
	PayBill("Pay Bill"),
	GetOpeningHours("Get Opening Hours"),

    ShowAllOrders("Show All Orders"),
    RegisterNewSubscriber("Register New Subscriber"),
    EditOpeningHours("Edit Opening Hours"),
    AddTable("Add Table"),
    EditTable("Edit Table"),
    CurrentCustomers("Current Customers"),
    GetWaitingList("Get Waiting List"),
    CurrentReservations("Current Reservations"),
    SubInfo("Sub Info"),
    ValidateUserID("Validate User ID"),
    DelayChartReport("Delay Chart Report"),
    ReservationChartReport("Reservation Chart Report"),
    GetSubscriberTodayConfirmationCodes("Get Subscriber Today Confirmation Codes");

    /** The human-readable display name for this menu option */
    private final String displayName;

    /**
     * Constructor to set the display name for each enum value.
     * 
     * @param displayName The display name for this menu option
     */
    UserSelect(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name for this menu option.
     * 
     * @return The display name string
     */
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
