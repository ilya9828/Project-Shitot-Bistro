package gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import client.ChatClient;
import client.ClientUI;
import common.UserSelect;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Controller for Staff Menu screen.
 * Provides button-based menu options for staff members.
 */
public class StaffMenuController {
// needs to extend the BaseMenuController. 
	@FXML
	private Button subInfoButton;
	@FXML
	private Button currentReservationsButton;
	@FXML
	private Button getWaitingListButton;
	@FXML
	private Button currentCustomersButton;
	@FXML
	private Button editTableButton;
	@FXML
	private Button addTableButton;
	@FXML
	private Button editOpeningHoursButton;
	@FXML
	private Button registerNewSubscriberButton;
	@FXML
	private Button backToLoginButton;

	/**
	 * Handles Sub Info button click.
	 */
	@FXML
	private void handleSubInfo() {
		navigateToScreen("SubInfo", "Sub Info", UserSelect.SubInfo);
	}

	/**
	 * Handles Current Reservations button click.
	 */
	@FXML
	private void handleCurrentReservations() {
		navigateToScreen("CurrentReservations", "Current Reservations", UserSelect.CurrentReservations);
	}

	/**
	 * Handles Get Waiting List button click.
	 */
	@FXML
	private void handleGetWaitingList() {
		navigateToScreen("GetWaitingList", "Get Waiting List", UserSelect.GetWaitingList);
	}

	/**
	 * Handles Current Customers button click.
	 */
	@FXML
	private void handleCurrentCustomers() {
		navigateToScreen("CurrentCustomers", "Current Customers", UserSelect.CurrentCustomers);
	}

	/**
	 * Handles Edit Table button click.
	 */
	@FXML
	private void handleEditTable() {
		navigateToScreen("EditTable", "Edit Table", UserSelect.EditTable);
	}

	/**
	 * Handles Add Table button click.
	 */
	@FXML
	private void handleAddTable() {
		navigateToScreen("AddTable", "Add Table", UserSelect.AddTable);
	}

	/**
	 * Handles Edit Opening Hours button click.
	 */
	@FXML
	private void handleEditOpeningHours() {
		navigateToScreen("EditOpeningHours", "Edit Opening Hours", UserSelect.EditOpeningHours);
	}

	/**
	 * Handles Register New Subscriber button click.
	 */
	@FXML
	private void handleRegisterNewSubscriber() {
		navigateToScreen("RegisterNewSubscriber", "Register New Subscriber", UserSelect.RegisterNewSubscriber);
	}

	/**
	 * Handles Back to Login button click.
	 * Navigates back to User Identification screen.
	 */
	@FXML
	private void handleBackToLogin() {
		try {
			common.UserSessionHelper.reset();
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/UserIdentification.fxml"));
			Parent root = loader.load();

			Stage stage = new Stage();
			Scene scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource("/gui/UserIdentification.css").toExternalForm());
			stage.setScene(scene);
			stage.setTitle("User Identification");

			stage.setOnCloseRequest(closeEvent -> {
				try {
					if (ClientUI.chat != null) {
						HashMap<String, String> disconnectMsg = new HashMap<>();
						disconnectMsg.put("Disconnect", "");
						ClientUI.chat.accept(disconnectMsg);
						Thread.sleep(200);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});

			stage.show();
			// Hide current window
			Stage currentStage = (Stage) backToLoginButton.getScene().getWindow();
			currentStage.hide();
		} catch (IOException e) {
			System.err.println("Failed to load User Identification screen: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Generic method to navigate to a screen with optional data loading.
	 * 
	 * @param screenName The name of the FXML file (without .fxml extension)
	 * @param title The title for the stage
	 * @param userSelect The UserSelect enum value for this screen
	 */
	private void navigateToScreen(String screenName, String title, UserSelect userSelect) {
		try {
			FXMLLoader loader = new FXMLLoader();
			
			// Hide current window
			Stage currentStage = (Stage) subInfoButton.getScene().getWindow();
			currentStage.hide();
			
			Stage primaryStage = new Stage();
			Pane root = loader.load(getClass().getResource("/gui/" + screenName + ".fxml").openStream());
			
			// Handle data loading for table-based screens
			switch (userSelect) {
			case SubInfo:
				loadSubInfo(loader);
				break;
			case CurrentReservations:
				loadCurrentReservations(loader);
				break;
			case GetWaitingList:
				loadGetWaitingList(loader);
				break;
			case CurrentCustomers:
				loadCurrentCustomers(loader);
				break;
			case EditTable:
			case AddTable:
			case EditOpeningHours:
			case RegisterNewSubscriber:
				// These screens don't need data pre-loading
				break;
			default:
				break;
			}

			Scene scene = new Scene(root);
			scene.getStylesheets().add(getClass().getResource("/gui/" + screenName + ".css").toExternalForm());
			primaryStage.setTitle(title);
			primaryStage.setScene(scene);
			
			// Handle window close
			primaryStage.setOnCloseRequest(closeEvent -> {
				try {
					if (ClientUI.chat != null) {
						HashMap<String, String> disconnectMsg = new HashMap<>();
						disconnectMsg.put("Disconnect", "");
						ClientUI.chat.accept(disconnectMsg);
						Thread.sleep(200);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
			
			primaryStage.show();
		} catch (Exception e) {
			System.err.println("Failed to load " + screenName + " screen: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Loads Sub Info data from server.
	 */
	private void loadSubInfo(FXMLLoader loader) {
		ChatClient.subInfoTable.clear();
		// Setting the type of the expectedListType
		ChatClient.expectedListType = "subInfo";
		Callable<Void> task = () -> {
			Platform.runLater(() -> {
				HashMap<String, String> ShowSubInfo = new HashMap<String, String>();
				ShowSubInfo.put("SubInfo", "");
				ClientUI.chat.accept(ShowSubInfo);
				SubInfoController controller = loader.getController();
				if (ChatClient.subInfoTable != null) {
					controller.loadSubInfo(ChatClient.subInfoTable);
				} else {
					controller.loadSubInfo(new java.util.ArrayList<>());
				}
			});
			return null;
		};
		FutureTask<Void> futureTask = new FutureTask<>(task);
		new Thread(futureTask).start();
		try {
			futureTask.get();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads Current Reservations data from server.
	 */
	private void loadCurrentReservations(FXMLLoader loader) {
		ChatClient.currentReservationsTable.clear();
		// Setting the type of the expectedListType
		ChatClient.expectedListType = "currentReservations";
		Callable<Void> task = () -> {
			Platform.runLater(() -> {
				HashMap<String, String> ShowCurrentReservations = new HashMap<String, String>();
				ShowCurrentReservations.put("CurrentReservations", "");
				ClientUI.chat.accept(ShowCurrentReservations);
				CurrentReservationsController controller = loader.getController();
				if (ChatClient.currentReservationsTable != null) {
					controller.loadCurrentReservations(ChatClient.currentReservationsTable);
				} else {
					controller.loadCurrentReservations(new java.util.ArrayList<>());
				}
			});
			return null;
		};
		FutureTask<Void> futureTask = new FutureTask<>(task);
		new Thread(futureTask).start();
		try {
			futureTask.get();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads Get Waiting List data from server.
	 */
	private void loadGetWaitingList(FXMLLoader loader) {
		ChatClient.waitingListTable.clear();
		// Setting the type of the expectedListType
		ChatClient.expectedListType = "waitingList";
		Callable<Void> task = () -> {
			Platform.runLater(() -> {
				HashMap<String, String> ShowWaitingList = new HashMap<String, String>();
				ShowWaitingList.put("GetWaitingList", "");
				ClientUI.chat.accept(ShowWaitingList);
				GetWaitingListController controller = loader.getController();
				if (ChatClient.waitingListTable != null) {
					controller.loadWaitingList(ChatClient.waitingListTable);
				} else {
					controller.loadWaitingList(new java.util.ArrayList<>());
				}
			});
			return null;
		};
		FutureTask<Void> futureTask = new FutureTask<>(task);
		new Thread(futureTask).start();
		try {
			futureTask.get();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads Current Customers data from server.
	 */
	private void loadCurrentCustomers(FXMLLoader loader) {
		ChatClient.occupiedTablesTable.clear();
		// Setting the type of the expectedListType
		ChatClient.expectedListType = "occupiedTables";
		Callable<Void> task = () -> {
			Platform.runLater(() -> {
				HashMap<String, String> ShowOccupiedTables = new HashMap<String, String>();
				ShowOccupiedTables.put("CurrentCustomers", "");
				ClientUI.chat.accept(ShowOccupiedTables);
				CurrentCustomersController controller = loader.getController();
				if (ChatClient.occupiedTablesTable != null) {
					controller.loadOccupiedTables(ChatClient.occupiedTablesTable);
				} else {
					controller.loadOccupiedTables(new java.util.ArrayList<>());
				}
			});
			return null;
		};
		FutureTask<Void> futureTask = new FutureTask<>(task);
		new Thread(futureTask).start();
		try {
			futureTask.get();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method is for the exit button sending a message to the server that now we are disconnecting,
	 * closing the GUI and the connection for the server.
	 */
	@FXML
	public void getExitBtn(ActionEvent event) throws Exception {
		System.out.println("Disconnecting from the Server and ending the program.");
		HashMap<String, String> EndingConnections = new HashMap<String, String>();
		EndingConnections.put("Disconnect", "");
		ClientUI.chat.accept(EndingConnections);
		System.exit(0);
	}
}
