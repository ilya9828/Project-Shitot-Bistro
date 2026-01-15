package common;
/*
 * This enum is defining the action select of the user
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
