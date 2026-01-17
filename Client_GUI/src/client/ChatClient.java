package client;

import ocsf.client.*;
import common.ChatIF;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Client class extending AbstractClient for handling bidirectional communication
 * between the client UI and the server. Manages message sending, receiving,
 * and stores various data tables received from the server.
 * 
 * @author Project Team
 * @version 1.0
 */
public class ChatClient extends AbstractClient {

	/** Reference to the client UI interface for displaying messages */
	ChatIF clientUI;
	
	/** Static list storing order information received from the server */
	public static List<String> ordersTable = new ArrayList<String>();
	
	/** Static list storing occupied table information received from the server */
	public static List<String> occupiedTablesTable = new ArrayList<String>();
	
	/** Static list storing waiting list entries received from the server */
	public static List<String> waitingListTable = new ArrayList<String>();
	
	/** Static list storing current reservations received from the server */
	public static List<String> currentReservationsTable = new ArrayList<String>();
	
	/** Static list storing subscriber information received from the server */
	public static List<String> subInfoTable = new ArrayList<String>();
	
	/** Static list storing subscriber confirmation codes received from the server */
	public static List<String> subscriberConfirmationCodes = new ArrayList<String>();
	
	/** Static list storing delay chart report data received from the server */
	public static List<String> delayChartReportData = new ArrayList<String>();
	
	/** Static list storing reservation chart report data received from the server */
	public static List<String> reservationChartReportData = new ArrayList<String>();
	
	/** Static string storing general messages received from the server */
	public static String fromserverString = new String();
	
	/** Flag indicating whether the client is waiting for a response from the server */
	public static boolean awaitResponse = false;
	
	/** 
	 * Expected list type identifier for routing received data to the correct table.
	 * Valid values: "orders", "occupiedTables", "waitingList", "currentReservations",
	 * "subInfo", "subscriberConfirmationCodes", "delayChartReport", "reservationChartReport"
	 */
	public static String expectedListType = "";

	/**
	 * Constructs a new ChatClient instance.
	 * Initializes the connection to the specified host and port.
	 * 
	 * @param host The server host address
	 * @param port The server port number
	 * @param clientUI The ChatIF interface for message display
	 * @throws IOException if the connection setup fails
	 */
	public ChatClient(String host, int port, ChatIF clientUI) throws IOException {
		super(host, port); // Call the superclass constructor
		this.clientUI = clientUI;
		// openConnection();
	}

	/**
	 * Handles messages received from the server.
	 * Routes the message to the appropriate data structure based on type:
	 * - List&lt;String&gt;: Populates the corresponding table based on expectedListType or content analysis
	 * - String: Stores in fromserverString for general server responses
	 * 
	 * @param msg The message object received from the server (either List&lt;String&gt; or String)
	 */
	@SuppressWarnings("unchecked")
	public void handleMessageFromServer(Object msg) {
		//extracting the msg from the server upon the class.
		if (msg instanceof List) {
			List<String> receivedList = (List<String>) msg;
			// Determine which list to populate based on expected type or content
			if (!expectedListType.isEmpty()) {
				// Use the expected list type if set
				if (expectedListType.equals("waitingList")) {
					waitingListTable = receivedList;
				} else if (expectedListType.equals("occupiedTables")) {
					occupiedTablesTable = receivedList;
				} else if (expectedListType.equals("currentReservations")) {
					currentReservationsTable = receivedList;
				} else if (expectedListType.equals("subInfo")) {
					subInfoTable = receivedList;
				} else if (expectedListType.equals("subscriberConfirmationCodes")) {
					subscriberConfirmationCodes = receivedList;
				} else if (expectedListType.equals("delayChartReport")) {
					delayChartReportData = receivedList;
					// System.out.println("✅ Received delay chart report data: " + (receivedList != null ? receivedList.size() + " items" : "null"));
					// if (receivedList != null && !receivedList.isEmpty()) {
					//     System.out.println("   First item: " + receivedList.get(0));
					// }
				} else if (expectedListType.equals("reservationChartReport")) {
					reservationChartReportData = receivedList;
					// System.out.println("✅ Received reservation chart report data: " + (receivedList != null ? receivedList.size() + " items" : "null"));
					// if (receivedList != null && !receivedList.isEmpty()) {
					//     System.out.println("   First item: " + receivedList.get(0));
					// }
				} else {
					ordersTable = receivedList;
				}
				expectedListType = ""; // Reset after use
				awaitResponse = false; // Reset await flag when data is received
			} else {
				// Fallback: Determine which list to populate based on content
				// Orders have 6 fields, occupied tables have 5 fields, subscribers have 4 fields
				if (!receivedList.isEmpty()) {
					String firstItem = receivedList.get(0);
					String[] parts = firstItem.split(", ");
					if (parts.length == 4) {
						// Subscriber info (format: "subscriberID, name, email, phone")
						subInfoTable = receivedList;
					} else if (parts.length == 5) {
						// Occupied tables (format: "tableID, capacity, customerName, checkInTime, confirmationCode")
						occupiedTablesTable = receivedList;
					} else if (parts.length == 6) {
						// Check first field: numeric = waiting list, alphanumeric = orders
						String firstField = parts[0].trim();
						if (firstField.matches("\\d+")) {
							// Waiting list (waitingID is numeric)
							waitingListTable = receivedList;
						} else {
							// Orders or Current Reservations (has 6 fields)
							ordersTable = receivedList;
						}
					} else {
						// Default to orders for backward compatibility
						ordersTable = receivedList;
					}
				} else {
					// Empty list - default to orders
					ordersTable = receivedList;
				}
			}
		}
		else if (msg.getClass().equals(fromserverString.getClass())) {
			ChatClient.fromserverString = (String) msg;
		}

		else {
			System.out.println("The return from the server class is: " + msg.getClass());
		}
		//warning for debugging.
		awaitResponse = false;
	}

	/**
	 * Resets the server response string to empty.
	 * Should be called after reading fromserverString to ensure
	 * subsequent reads can distinguish new messages from old ones.
	 */
	public static void ResetServerString() {
		fromserverString = new String();
	}

	
	/**
	 * Processes a message from the client UI and sends it to the server.
	 * Opens the connection if not already open, sends the message, and waits
	 * for a response. Terminates the client if sending fails.
	 * 
	 * @param obj The message object to send to the server (typically a HashMap)
	 */
	public void handleMessageFromClientUI(Object obj) {
		try {
			openConnection();// in order to send more than one message
			awaitResponse = true;
			sendToServer(obj);
			// wait for response
			while (awaitResponse) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			clientUI.display("Could not send message to server: Terminating client." + e);
			quit();
		}
	}

	/**
	 * This method terminates the client.
	 */
	public void quit() {
		try {
			closeConnection();
		} catch (IOException e) {
		}
		System.exit(0);
	}
}
