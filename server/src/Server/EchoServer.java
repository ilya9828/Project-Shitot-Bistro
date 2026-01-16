package Server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import common.UserSelect;
import entities.Reservations;
import gui.ClientConnectionStatusController;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ocsf.server.*;
import entities.WaitingEntry;
import entities.Payment;
/*
 * This class is defining as a server
 */
public class EchoServer extends AbstractServer {
	public static HashMap<String, String> clientsstatusconnections = new HashMap<String, String>();
	public static String popUpString;
	public static Scene tableScene; 
	public static Stage tableStage; 
	public static ClientConnectionStatusController tableController;
	

	/** Constructs an instance of the echo server.
	 * @param port - The port number to connect on.
	 */
	public EchoServer(int port) {
		super(port);
	}


	/**
	 * This method handles any messages received from the client.
	 *
	 * @param msg    The message received from the client.
	 * @param client The connection from which the message originated.
	 */
	public void handleMessageFromClient(Object msg, ConnectionToClient client) {
		int flag = 0;
		String clientIp = client.getInetAddress().getHostAddress();
		String clientPCName = client.getInetAddress().getHostName();
		
		// ===== RESERVE TABLE =====
		if (msg instanceof Reservations) {

		    Reservations reservation = (Reservations) msg;

		    String result = mysqlConnection.insertReservation(reservation);

		    if (result.equals("SUCCESS")) {
		        this.sendToAllClients("ReservationAdded");
		    } else if (result.startsWith("NO_CAPACITY:")) {
		        // Send the result with alternative times
		        this.sendToAllClients(result);
		    } else if (result.startsWith("INVALID_DATE_TIME:")) {
		        // Send the invalid date/time error message
		        this.sendToAllClients(result);
		    } else {
		        this.sendToAllClients("Error");
		    }

		    return;
		}

				// ===== WAITING LIST =====
		if (msg instanceof WaitingEntry) {
		    WaitingEntry entry = (WaitingEntry) msg;
		    String result = mysqlConnection.insertWaitingEntry(entry);
		    
		    if (result.startsWith("TABLE_AVAILABLE:") || result.startsWith("WAITING_LIST:")) {
		        // Send the result (either table location or confirmation code)
		        this.sendToAllClients(result);
		    } else {
		        this.sendToAllClients("Error");
		    }
		    
		    return;
		}
		// ===== PAYMENT =====
		if (msg instanceof Payment) {
		    Payment payment = (Payment) msg;
		    String result = mysqlConnection.processPayment(payment);
		    this.sendToAllClients(result);
		    return;
		}



		
		// Type check before casting to avoid unchecked cast warning
		if (!(msg instanceof HashMap)) {
			System.out.println("Error: Received unexpected message type: " + msg.getClass().getName());
			this.sendToAllClients("Error");
			return;
		}
		
		@SuppressWarnings("unchecked")
		HashMap<String, String> infoFromUser = (HashMap<String, String>) msg;
		String menuChoiceString = (infoFromUser.keySet().iterator().next());
		
		UserSelect x = UserSelect.getSelectionFromEnumName(menuChoiceString);
		
		switch (x) {
		//This case is getting the information to change from the user and saving in DB.
		case UpdateOrderDate:
			
			// value format: "<confirmation_code> <timestamp>"
			// use split limit 2 so the full timestamp (with space) stays in index 1
			String orderDate[] = infoFromUser.get(menuChoiceString).split(" ", 2);
			String updateResult = mysqlConnection.updateOrderDate(orderDate[0], java.sql.Timestamp.valueOf(orderDate[1]));
			if ("Updated".equals(updateResult)) {
				this.sendToAllClients("Updated");
			} else if (updateResult.startsWith("NO_CAPACITY:")) {
				// Send the result with alternative times (if available)
				this.sendToAllClients(updateResult);
			} else {
				this.sendToAllClients("Error");
			}

			flag++;
			break;
		case UpdateNumberOfGuests:
			String numOfguests[] = infoFromUser.get(menuChoiceString).split(" ");
			boolean succ2 = mysqlConnection.updateNumOfGuests(numOfguests[0], Integer.parseInt(numOfguests[1]));
			if (succ2) {
				this.sendToAllClients("Updated");
			} else {
				this.sendToAllClients("Error");
			}
			flag++;
			break;
			
		case CheckIn:
			String CheckIn = mysqlConnection.CheckIn(infoFromUser.get(menuChoiceString));
			this.sendToAllClients(CheckIn);
			flag++;
			break;
			
		case LostCode:
			String Code = mysqlConnection.LostCode(infoFromUser.get(menuChoiceString));
			this.sendToAllClients(Code);
			System.out.println("Code: " + Code);
			flag++;
			break;
		case ExitWaitingList:
			String exitResult = mysqlConnection.exitWaitingList(infoFromUser.get(menuChoiceString));
			this.sendToAllClients(exitResult);
			flag++;
			break;
		case JoinWaitingListSub:
			// value format: "<subscriberID> <number_of_guests>"
			String[] joinParts = infoFromUser.get(menuChoiceString).split(" ");
			if (joinParts.length == 2) {
				String subIdStr = joinParts[0];
				int guests = Integer.parseInt(joinParts[1]);
				String joinResult = mysqlConnection.insertWaitingEntryForSubscriber(subIdStr, guests);
				if (joinResult.startsWith("TABLE_AVAILABLE:") || joinResult.startsWith("WAITING_LIST:")) {
					// Send the result (either table location or confirmation code)
					this.sendToAllClients(joinResult);
				} else {
					this.sendToAllClients("Error");
				}
			} else {
				this.sendToAllClients("Error");
			}
			flag++;
			break;
		case ExitWaitingListSub:
			String subIdForExit = infoFromUser.get(menuChoiceString);
			String exitResultSub = mysqlConnection.exitWaitingListForSubscriber(subIdForExit);
			this.sendToAllClients(exitResultSub);
			flag++;
			break;
		case ValidateSubscriber:
			String subid = infoFromUser.get(menuChoiceString);
			boolean isValid = mysqlConnection.validateSubscriber(subid);
			if (isValid) {
				this.sendToAllClients("SubscriberValid");
			} else {
				this.sendToAllClients("SubscriberInvalid");
			}
			flag++;
			break;
		case UpdateEmailOrPhone:
			// value format: "<confirmation_code> <contact>"
			String contactParts[] = infoFromUser.get(menuChoiceString).split(" ", 2);
			boolean contactSucc = mysqlConnection.updateEmailOrPhone(contactParts[0], contactParts[1]);
			if (contactSucc) {
				this.sendToAllClients("Updated");
			} else {
				this.sendToAllClients("Error");
			}
			flag++;
			break;
				
		case GetSubscriberInfo:
			String subscriberID = infoFromUser.get(menuChoiceString);
			String subscriberInfo = mysqlConnection.getSubscriberInfo(subscriberID);
			this.sendToAllClients(subscriberInfo);
			flag++;
			break;
		case UpdateSubscriberInfo:
			// value format: "subscriberID|name|email|phone"
			String updateData[] = infoFromUser.get(menuChoiceString).split("\\|", 4);
			if (updateData.length == 4) {
				boolean updateSucc = mysqlConnection.updateSubscriberInfo(updateData[0], updateData[1], updateData[2], updateData[3]);
				if (updateSucc) {
					this.sendToAllClients("Updated");
				} else {
					this.sendToAllClients("Error");
				}
			} else {
				this.sendToAllClients("Error");
			}
			flag++;
			break;
		case GetSubscriberHistory:
			String subscriberIDForHistory = infoFromUser.get(menuChoiceString);
			List<String> history = mysqlConnection.getSubscriberHistory(subscriberIDForHistory);
			this.sendToAllClients(history);
			flag++;
			break;
		case CancelReservation:
			String confirmationCodeToCancel = infoFromUser.get(menuChoiceString);
			String cancelResult = mysqlConnection.cancelReservation(confirmationCodeToCancel);
			this.sendToAllClients(cancelResult);
			flag++;
			break;
		case GetOpeningHours:
			String date = infoFromUser.get(menuChoiceString);
			String openingHours = mysqlConnection.getOpeningHours(date);
			// Send the opening hours or "DEFAULT" if not found
			this.sendToAllClients(openingHours != null ? openingHours : "DEFAULT");
			flag++;
			break;

		//This case is registering a new subscriber in the DB.
		case RegisterNewSubscriber:
			try {
				String subscriberData = infoFromUser.get(menuChoiceString);
				String[] parts = subscriberData.split("\\|");
				if (parts.length == 4) {
					String fullName = parts[0];
					String phone = parts[1];
					String subscriberId = parts[2];
					String email = parts[3];
					System.out.println("Registering subscriber: " + fullName + ", Phone: " + phone);
					String result = mysqlConnection.insertSubscriber(fullName, phone, subscriberId, email);
					System.out.println("Insert result: " + result);
					this.sendToAllClients(result);
				} else {
					System.err.println("Error: Expected 4 parts, got " + parts.length + ". Data: " + subscriberData);
					this.sendToAllClients("Error: Invalid data format");
				}
			} catch (Exception e) {
				System.err.println("Exception in RegisterNewSubscriber case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Error: " + e.getMessage());
			}
			flag++;
			break;
		//This case is editing opening hours in the DB.
		case EditOpeningHours:
			try {
				String openingHoursData = infoFromUser.get(menuChoiceString);
				// Split on "|" but handle case where durationValue may contain "|"
				// Format: openingTime|closingTime|durationType|durationValue
				// For SPECIFIC_DAY, durationValue is "dayOfWeek|date", so we need special handling
				int firstPipe = openingHoursData.indexOf("|");
				int secondPipe = openingHoursData.indexOf("|", firstPipe + 1);
				int thirdPipe = openingHoursData.indexOf("|", secondPipe + 1);
				
				if (firstPipe > 0 && secondPipe > firstPipe && thirdPipe > secondPipe) {
					String openingTime = openingHoursData.substring(0, firstPipe);
					String closingTime = openingHoursData.substring(firstPipe + 1, secondPipe);
					String durationType = openingHoursData.substring(secondPipe + 1, thirdPipe);
					String durationValue = openingHoursData.substring(thirdPipe + 1); // Everything after third pipe
					
					System.out.println("Editing opening hours - Time: " + openingTime + "-" + closingTime + ", Type: " + durationType + ", Value: " + durationValue);
					String result = mysqlConnection.updateOpeningHours(openingTime, closingTime, durationType, durationValue);
					System.out.println("Update result: " + result);
					this.sendToAllClients(result);
				} else {
					System.err.println("Error: Invalid data format. Data: " + openingHoursData);
					this.sendToAllClients("Error: Invalid data format");
				}
			} catch (Exception e) {
				System.err.println("Exception in EditOpeningHours case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Error: " + e.getMessage());
			}
			flag++;
			break;
		case AddTable:
			try {
				String capacityStr = infoFromUser.get(menuChoiceString);
				int capacity = Integer.parseInt(capacityStr);
				System.out.println("Adding new table with capacity: " + capacity);
				String result = mysqlConnection.insertTable(capacity);
				System.out.println("Insert result: " + result);
				this.sendToAllClients(result);
			} catch (NumberFormatException e) {
				System.err.println("Error: Invalid capacity format. Data: " + infoFromUser.get(menuChoiceString));
				this.sendToAllClients("Error: Invalid capacity format");
			} catch (Exception e) {
				System.err.println("Exception in AddTable case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Error: " + e.getMessage());
			}
			flag++;
			break;
		case GetNextTableId:
			try {
				System.out.println("Getting next table ID");
				String nextId = mysqlConnection.getNextTableId();
				System.out.println("Next table ID: " + nextId);
				this.sendToAllClients(nextId);
			} catch (Exception e) {
				System.err.println("Exception in GetNextTableId case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Error: " + e.getMessage());
			}
			flag++;
			break;
		case GetAllTableIds:
			try {
				System.out.println("Getting all table IDs");
				String tableIds = mysqlConnection.getAllTableIds();
				System.out.println("Table IDs: " + tableIds);
				this.sendToAllClients(tableIds);
			} catch (Exception e) {
				System.err.println("Exception in GetAllTableIds case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Error: " + e.getMessage());
			}
			flag++;
			break;
		case GetTableData:
			try {
				String tableId = infoFromUser.get(menuChoiceString);
				System.out.println("Getting data for table ID: " + tableId);
				String capacity = mysqlConnection.getTableData(tableId);
				System.out.println("Table capacity: " + capacity);
				this.sendToAllClients(capacity);
			} catch (Exception e) {
				System.err.println("Exception in GetTableData case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Error: " + e.getMessage());
			}
			flag++;
			break;
		case UpdateTable:
			try {
				String updateData1 = infoFromUser.get(menuChoiceString);
				// Format: "tableID|capacity"
				String[] parts = updateData1.split("\\|");
				if (parts.length == 2) {
					int tableId = Integer.parseInt(parts[0]);
					int capacity = Integer.parseInt(parts[1]);
					System.out.println("Updating table " + tableId + " with capacity " + capacity);
					String result = mysqlConnection.updateTable(tableId, capacity);
					System.out.println("Update result: " + result);
					this.sendToAllClients(result);
				} else {
					System.err.println("Error: Expected 2 parts, got " + parts.length);
					this.sendToAllClients("Error: Invalid data format");
				}
			} catch (Exception e) {
				System.err.println("Exception in UpdateTable case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Error: " + e.getMessage());
			}
			flag++;
			break;
		case DeleteTable:
			try {
				String tableIdStr = infoFromUser.get(menuChoiceString);
				int tableId = Integer.parseInt(tableIdStr);
				System.out.println("Deleting table ID: " + tableId);
				String result = mysqlConnection.deleteTable(tableId);
				System.out.println("Delete result: " + result);
				this.sendToAllClients(result);
			} catch (Exception e) {
				System.err.println("Exception in DeleteTable case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Error: " + e.getMessage());
			}
			flag++;
			break;
		case CurrentCustomers:
			try {
				System.out.println("Getting occupied tables");
				List<String> occupiedTables = mysqlConnection.GetOccupiedTables();
				System.out.println("Found " + occupiedTables.size() + " occupied tables");
				this.sendToAllClients(occupiedTables);
			} catch (Exception e) {
				System.err.println("Exception in CurrentCustomers case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients(new ArrayList<>()); // Send empty list on error
			}
			flag++;
			break;
		case GetWaitingList:
			try {
				System.out.println("Getting waiting list");
				List<String> waitingList = mysqlConnection.GetWaitingList();
				System.out.println("Found " + waitingList.size() + " waiting entries");
				this.sendToAllClients(waitingList);
			} catch (Exception e) {
				System.err.println("Exception in GetWaitingList case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients(new ArrayList<>()); // Send empty list on error
			}
			flag++;
			break;
		case CurrentReservations:
			try {
				System.out.println("Getting current reservations");
				List<String> currentReservations = mysqlConnection.GetCurrentReservations();
				System.out.println("Found " + currentReservations.size() + " current reservations");
				this.sendToAllClients(currentReservations);
			} catch (Exception e) {
				System.err.println("Exception in CurrentReservations case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients(new ArrayList<>()); // Send empty list on error
			}
			flag++;
			break;
		case SubInfo:
			try {
				System.out.println("Getting subscriber info");
				List<String> subInfo = mysqlConnection.GetSubInfo();
				System.out.println("Found " + subInfo.size() + " subscribers");
				this.sendToAllClients(subInfo);
			} catch (Exception e) {
				System.err.println("Exception in SubInfo case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients(new ArrayList<>()); // Send empty list on error
			}
			flag++;
			break;
		case GetSubscriberTodayConfirmationCodes:
			try {
				String subscriberIDForCodes = infoFromUser.get(menuChoiceString);
				System.out.println("Getting today confirmation codes for subscriber: " + subscriberIDForCodes);
				List<String> confirmationCodes = mysqlConnection.GetSubscriberTodayConfirmationCodes(subscriberIDForCodes);
				System.out.println("Found " + confirmationCodes.size() + " confirmation codes for today");
				this.sendToAllClients(confirmationCodes);
			} catch (Exception e) {
				System.err.println("Exception in GetSubscriberTodayConfirmationCodes case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients(new ArrayList<>()); // Send empty list on error
			}
			flag++;
			break;
		//This case is loading the requested ID from the DB and sending to the client.
		// FIX: Changed to use confirmation_code (String) instead of order_number (int)
		case LoadOrders:
			String confirmationCode = infoFromUser.get(menuChoiceString);
			String RequestedID = mysqlConnection.Load(confirmationCode);
			this.sendToAllClients(RequestedID);
			flag++;
			break;
		//This case is Showing the client that connect to the server and showing it on the table GUI and shows a message.
		case Connected:
			String onlineStatuString = (clientIp + ", " + clientPCName + ", Connected");
			popUpString = "Client from IP: " + clientIp + ", HostName: " + clientPCName + ", Status: Connected";
			//System.out.println("Client from IP: " + clientIp + ", HostName: " + clientPCName + ", Status: Connected");
			if(clientsstatusconnections.containsKey(clientIp)) {
				clientsstatusconnections.remove(clientIp);
			}
			clientsstatusconnections.put(clientIp, onlineStatuString);
			tableController.setconnection(popUpString);
			this.sendToAllClients("Connected");
			flag++;
			break;
		//This case is Showing the client that disconnected to the server and showing it on the table GUI and shows a message.
		case Disconnect:
			String offlineStatuString = (clientIp + ", " + clientPCName + ", Disconnected");
			popUpString = "Client from IP: " + clientIp + ", HostName: " + clientPCName + ", Status: Disconnected";
			//System.out.println("Client from IP: " + clientIp + ", HostName: " + clientPCName + ", Status: Disconnected");
			clientsstatusconnections.remove(clientIp);
			clientsstatusconnections.put(clientIp, offlineStatuString);
			tableController.setconnection(popUpString);
			this.sendToAllClients("Disconnected");
			flag++;
			break;
		case ValidateUserID:
			try {
				String userId = infoFromUser.get(menuChoiceString);
				String userType = mysqlConnection.validateUserID(userId);
				this.sendToAllClients(userType);
			} catch (Exception e) {
				System.err.println("Exception in ValidateUserID case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients("Invalid");
			}
			flag++;
			break;
			
		case DelayChartReport:
			try {
				System.out.println("Getting Delay Chart Report...");
				String monthsParam = infoFromUser.get(menuChoiceString);
				List<String> reportData;
				if (monthsParam != null && !monthsParam.isEmpty()) {
					// Custom months selected
					reportData = mysqlConnection.GetDelayChartReport(monthsParam);
				} else {
					// Default: previous month
					reportData = mysqlConnection.GetDelayChartReport();
				}
				this.sendToAllClients(reportData);
				System.out.println("Delay Chart Report sent to client");
			} catch (Exception e) {
				System.err.println("Exception in DelayChartReport case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients(new ArrayList<String>());
			}
			flag++;
			break;
		
		case ReservationChartReport:
			try {
				System.out.println("Getting Reservation Chart Report...");
				String monthsParam = infoFromUser.get(menuChoiceString);
				List<String> reportData;
				if (monthsParam != null && !monthsParam.isEmpty()) {
					// Custom months selected
					reportData = mysqlConnection.GetReservationChartReport(monthsParam);
				} else {
					// Default: previous month
					reportData = mysqlConnection.GetReservationChartReport();
				}
				this.sendToAllClients(reportData);
				System.out.println("Reservation Chart Report sent to client");
			} catch (Exception e) {
				System.err.println("Exception in ReservationChartReport case: " + e.getMessage());
				e.printStackTrace();
				this.sendToAllClients(new ArrayList<String>());
			}
			flag++;
			break;
		//error with the userselect action.
		default:
			System.out.println("Error with the choise? = " + menuChoiceString);
			break;

		}
		if (flag != 1) {
			this.sendToAllClients("Error");
		}
	}


	/**
	 * This method overrides the one in the superclass. Called when the server stops
	 * listening for connections.
	 */
	protected void serverStopped() {
		System.out.println("Server has stopped listening for connections.");
	}

	
}
//End of EchoServer class
