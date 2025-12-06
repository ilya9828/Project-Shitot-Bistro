package Server;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/*
 * This class is connect to mySQL DB for G3-prototype server. 
 */
public class mysqlConnection {

	public static Connection conn;

	/*
	 * This method is connecting to out G3-prototype server
	 */
	public static String connectToDB() {
		String ret ="";
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			ret = ret + ("Driver definition succeed");

		} catch (Exception ex) {
			/* handle the error */
			ret = ret + ("Driver definition failed");
		}

		try {
			conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/bistro?allowLoadLocalInfile=true&serverTimezone=Asia/Jerusalem&useSSL=false","root","Aa123456");
			ret = ret + ("SQL connection succeed");
		} catch (SQLException ex) {/* handle any errors */
			
			ret = ret + ("SQLException: " + ex.getMessage());
			ret = ret + ("\nSQLState: " + ex.getSQLState());
			ret = ret + ("\nVendorError: " + ex.getErrorCode());
		}
		return ret;
		
	}

	/**
	 * This method is getting information to change in the DB 
	 * @param order_num		- order number (PK)
	 * @param num_of_guests	- number of guests to change to the order number
	 * @return True if success to save the changes
	 */
	public static boolean updateNumOfGuests(int order_num, int num_of_guests) {
		PreparedStatement stmt;
		try {
			stmt = conn.prepareStatement("UPDATE Orders SET number_of_guests = \"" + num_of_guests
					+ "\" WHERE  order_number = \"" + order_num + "\"");
			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * This method is getting information to change in the DB 
	 * @param order_num		- order number (PK)
	 * @param new_date	- new date to change to the order number
	 * @return True if success to save the changes
	 */
	public static boolean updateOrderDate(int order_num, Date new_date) {
		PreparedStatement stmt;
		try {
			stmt = conn.prepareStatement(
				    "UPDATE Orders SET order_date = ? WHERE order_number = ?"
				);
				stmt.setDate(1, new_date); 
				stmt.setInt(2, order_num);

				stmt.executeUpdate();

			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}


	/**
	 * This method is getting order number and returning String of its data
	 * @param order_num	- order number
	 * @return String of this order
	 */
	public static String Load(int order_num) {
		String query = "SELECT * FROM Orders WHERE  order_number = \"" + order_num + "\"";
		String orderData = new String("Empty");
		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			while (rs.next()) {
				int order_num1 = rs.getInt("order_number");
				Date date = rs.getDate("order_date");
				int num_guests = rs.getInt("number_of_guests");
				int con_code = rs.getInt("confirmation_code");
				int sub_id = rs.getInt("subscriber_id");
				Date date_placing_order = rs.getDate("date_of_placing_order");

				// Create a formatted string with the subscriber's information
				orderData = order_num1 + ", " + date + ", " + num_guests + ", " + con_code + ", " + sub_id + ", "+date_placing_order;
				return orderData;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return orderData;

	}

	
	/**
	 * This method is returning the list of orders from the DB to the client
	 * @return List of the orders
	 */
	public static List<String> GetOrdersTable() {
		List<String> orders = new ArrayList<>();
		String query = "SELECT * FROM Orders";

		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			while (rs.next()) {
				int order_num1 = rs.getInt("order_number");
				Date date = rs.getDate("order_date");
				int num_guests = rs.getInt("number_of_guests");
				int con_code = rs.getInt("confirmation_code");
				int sub_id = rs.getInt("subscriber_id");
				Date date_placing_order = rs.getDate("date_of_placing_order");

				// Create a formatted string with the subscriber's information
				String orderData = order_num1 + ", " + date + ", " + num_guests + ", " + con_code + ", " + sub_id+", "+date_placing_order;

				// Add the formatted string to the list
				orders.add(orderData);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return orders;
	}

}
