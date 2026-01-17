package common;

/**
 * Enumeration of all available user action selections on the server side.
 * Defines the menu options and operations that clients can request from the server.
 * Used to route client messages to appropriate handlers in the EchoServer class.
 * 
 * @author Project Team
 * @version 1.0
 */
public enum UserSelect {
    UpdateOrderDate("Update order Date"),
    UpdateNumberOfGuests("Update Number Of Guests"),
	LoadOrders("Load Orders"),
    CheckIn("Check In"),
	LostCode("Lost Code"),
	Connected("Connected"),
	Disconnect("Disconnect"),
	ValidateSubscriber("Validate Subscriber"),
	PayBill("Pay Bill"),
	ExitWaitingList("Exit Waiting List"),
	UpdateEmailOrPhone("Update Email Or Phone"),
	JoinWaitingListSub("Join Waiting List Subscriber"),
	ExitWaitingListSub("Exit Waiting List Subscriber"),
	GetSubscriberInfo("Get Subscriber Info"),
	UpdateSubscriberInfo("Update Subscriber Info"),
	GetSubscriberHistory("Get Subscriber History"),
	CancelReservation("Cancel Reservation"),
	GetOpeningHours("Get Opening Hours"),
    RegisterNewSubscriber("Register New Subscriber"),
    EditOpeningHours("Edit Opening Hours"),
    AddTable("Add Table"),
    GetNextTableId("Get Next Table Id"),
    EditTable("Edit Table"),
    GetAllTableIds("Get All Table Ids"),
    GetTableData("Get Table Data"),
    UpdateTable("Update Table"),
    DeleteTable("Delete Table"),
    CurrentCustomers("Current Customers"),
    GetWaitingList("Get Waiting List"),
    CurrentReservations("Current Reservations"),
    SubInfo("Sub Info"),
    ValidateUserID("Validate User ID"),
    DelayChartReport("Delay Chart Report"),
    ReservationChartReport("Reservation Chart Report"),
    GetSubscriberTodayConfirmationCodes("Get Subscriber Today Confirmation Codes");

    /** The human-readable display name for this action */
    private final String displayName;

    /**
     * Constructor to set the display name for each enum value.
     * 
     * @param displayName The display name for this action
     */
    UserSelect(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name for this action.
     * 
     * @return The display name string
     */
    public String getDisplayName() {
        return this.displayName;
    }
    
    /**
     * Retrieves the UserSelect enum value by matching the enum name (not display name).
     * Case-insensitive matching.
     * 
     * @param enumName The enum name string (e.g., "UpdateOrderDate")
     * @return The matching UserSelect enum, or null if not found
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
