// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 
package client;

import java.io.*;
import common.ChatIF;

/**
 * Controller class managing the connection between the client UI and the server.
 * Handles message routing and maintains the ChatClient instance for server communication.
 * Implements ChatIF interface to support message display functionality.
 * 
 * @author Project Team
 * @version 1.0
 */
public class ClientController implements ChatIF {
	/** The ChatClient instance handling the actual server connection */
	ChatClient client;

	// Constructors ****************************************************

	/**
	 * Constructs an instance of the ClientController.
	 * Initializes a new ChatClient connection to the specified host and port.
	 * Terminates the application if the connection setup fails.
	 *
	 * @param host The host address to connect to
	 * @param port The port number to connect on
	 */
	public ClientController(String host, int port) {
		try {
			client = new ChatClient(host, port, this);
		} catch (IOException exception) {
			System.out.println("Error: Can't setup connection!" + " Terminating client.");
			System.exit(1);
		}
	}

	// Instance methods ************************************************

	/**
	 * Accepts an object from the client UI and forwards it to the ChatClient
	 * for transmission to the server.
	 * 
	 * @param obj The object to send to the server (typically a HashMap)
	 */
	public void accept(Object obj) {
		client.handleMessageFromClientUI(obj);
	}

	/**
	 * Displays a message to the console.
	 * This method implements the ChatIF interface requirement.
	 * Currently outputs to System.out with a prefix.
	 *
	 * @param message The message string to be displayed
	 */
	public void display(String message) {
		System.out.println("> " + message);
	}
}
//End of ConsoleChat class
