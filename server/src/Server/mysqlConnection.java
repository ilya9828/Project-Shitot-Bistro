package Server;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;
import entities.Reservations;

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
	
	public static String CheckIn(String ConfirmationCode) {
		PreparedStatement stmt;
		try {
			stmt = conn.prepareStatement(
				    "UPDATE Orders SET status = ? WHERE confirmation_code = ?"
				);
				stmt.setString(1, "CheckedIN"); 
				stmt.setString(2, ConfirmationCode);

				stmt.executeUpdate();

			return "CheckInSuccess";
		} catch (SQLException e) {
			e.printStackTrace();
			return "CheckInFailed";
		}
	}
	


	public static String LostCode(String emailOrPhone) {
	    PreparedStatement stmt = null;
	    ResultSet rs = null;

	    try {
	        String query =
	            "SELECT confirmation_code " +
	            "FROM orders " +
	            "WHERE (email = ? OR phone = ?) " +
	            "AND order_time_date >= ? " +
	            "AND order_time_date < ? " +
	            "ORDER BY order_time_date DESC " +
	            "LIMIT 1";

	        // גבולות היום (00:00 עד 00:00 של מחר)
	        LocalDate today = LocalDate.now();
	        LocalDateTime startOfDay = today.atStartOfDay();
	        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();

	        stmt = conn.prepareStatement(query);
	        stmt.setString(1, emailOrPhone);
	        stmt.setString(2, emailOrPhone);
	        stmt.setTimestamp(3, Timestamp.valueOf(startOfDay));
	        stmt.setTimestamp(4, Timestamp.valueOf(startOfTomorrow));

	        rs = stmt.executeQuery();

	        if (rs.next()) {
	            String code = rs.getString("confirmation_code");
	            return "OrderCode:" + code;
	        } else {
	            return "LostOrderFailed";
	        }

	    } catch (SQLException e) {
	        e.printStackTrace();
	        return "ServerError";
	    } finally {
	        try {
	            if (rs != null) rs.close();
	            if (stmt != null) stmt.close();
	        } catch (SQLException e) {
	            e.printStackTrace();
	        }
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
	
	public static boolean insertReservation(Reservations reservation) {

	    String sql =
	        "INSERT INTO orders " +
	        "(subscriber_id, number_of_guests, confirmation_code, order_number, " +
	        "order_time_date, time_date_of_placing_order, status, is_subscriber, email, phone,name) " +
	        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)";

	    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

	        // 1️⃣ subscriber_id (יכול להיות NULL)
	        if (reservation.getSubscriberId() == null) {
	            pstmt.setNull(1, java.sql.Types.INTEGER);
	        } else {
	            pstmt.setInt(1, reservation.getSubscriberId());
	        }

	        // 2️⃣ number_of_guests
	        pstmt.setInt(2, reservation.getNumberOfGuests());

	        // 3️⃣ confirmation_code
	        pstmt.setString(3, reservation.getConfirmationCode());

	        // 4️⃣ order_number
	        pstmt.setString(4, reservation.getOrderNumber());

	        // 5️⃣ order_time&date (תאריך ושעת ההזמנה למסעדה)
	        pstmt.setTimestamp(5,Timestamp.valueOf(reservation.getOrderDateTime()));

	        // 6️⃣ time&date_of_placing_order (מתי הוזמנה ההזמנה)
	        pstmt.setTimestamp(6,Timestamp.valueOf(reservation.getPlacingOrderDate()));

	        // 7️⃣ status
	        pstmt.setString(7, reservation.getStatus());

	        // 8️⃣ is_subscriber
	        pstmt.setBoolean(8, reservation.isSubscriber());
	        
	        pstmt.setString(9, reservation.getEmail());
	        
	        pstmt.setString(10, reservation.getPhoneNumber());
	        
	        pstmt.setString(11, reservation.getName());

	        pstmt.executeUpdate();
	        return true;

	    } catch (SQLException e) {
	        System.err.println("❌ Error inserting reservation");
	        e.printStackTrace();
	        return false;
	    }
	}


}
