package client;

import ocsf.client.*;
import common.ChatIF;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is handling the messages from the client to the server and the other way.
 */
/**
 * 
 */
public class ChatClient extends AbstractClient {

	ChatIF clientUI;
	public static List<String> ordersTable = new ArrayList<String>();
	public static List<String> occupiedTablesTable = new ArrayList<String>();
	public static List<String> waitingListTable = new ArrayList<String>();
	public static List<String> currentReservationsTable = new ArrayList<String>();
	public static List<String> subInfoTable = new ArrayList<String>();
	public static List<String> subscriberConfirmationCodes = new ArrayList<String>();
	public static List<String> delayChartReportData = new ArrayList<String>();
	public static List<String> reservationChartReportData = new ArrayList<String>();
	public static String fromserverString = new String();
	public static boolean awaitResponse = false;
	public static String expectedListType = ""; // Track which list type we're expecting: "orders", "occupiedTables", "waitingList", "currentReservations", "subInfo", "subscriberConfirmationCodes", "delayChartReport", "reservationChartReport"

	public ChatClient(String host, int port, ChatIF clientUI) throws IOException {
		super(host, port); // Call the superclass constructor
		this.clientUI = clientUI;
		// openConnection();
	}

	/**
	 * This method is handeling the msg from the server, we got 2 options to get the
	 * msg: 
	 * 1- if we ask for all subscribers table we are expecting for a List<String> from the server.
	 * 2- if we asked to load one subscriber, or waiting for feedback from the server(we would expect String from the server)
	 * @param msg - the message from the server
	 */
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
	 * This method is for to reset the string after the client took the information
	 * to the UI so nex time he come for the string he knows for sure its new
	 * string.
	 */
	public static void ResetServerString() {
		fromserverString = new String();
	}

	
	/**This method is getting the message from the client and send it to the server.
	 * @param obj - object but the client sending hashmap to the server
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
