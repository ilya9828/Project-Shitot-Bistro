// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 
package client;

import java.io.*;
import common.ChatIF;

/**This class is the controller of connection to the server
 * and getting the information that we wants to send to the server.
 */
public class ClientController implements ChatIF {
	public static int DEFAULT_PORT;
	ChatClient client;

	// Constructors ****************************************************

	/**
	 * Constructs an instance of the ClientConsole UI.
	 *
	 * @param host The host to connect to.
	 * @param port The port to connect on.
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
	 * This method waits for input from the client. Once it is received, it sends
	 * it to the client's message handler.
	 */
	public void accept(Object obj) {
		client.handleMessageFromClientUI(obj);
	}

	/******************** Unused method but has to implements.    ********************
	 * This method overrides the method in the ChatIF interface. It displays a
	 * message onto the screen.
	 *
	 * @param message The string to be displayed.
	 */
	public void display(String message) {
		System.out.println("> " + message);
	}
}
//End of ConsoleChat class
