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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import entities.Reservations;

/*
 * This class is connect to mySQL DB for G3-prototype server. 
 */
public class mysqlConnection {

	public static Connection conn;
	private static ScheduledExecutorService reportScheduler = null;

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
			conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/bistrodb?allowLoadLocalInfile=true&serverTimezone=Asia/Jerusalem&useSSL=false","root","Aa123456");
			ret = ret + ("SQL connection succeed");
		} catch (SQLException ex) {/* handle any errors */
			
			ret = ret + ("SQLException: " + ex.getMessage());
			ret = ret + ("\nSQLState: " + ex.getSQLState());
			ret = ret + ("\nVendorError: " + ex.getErrorCode());
		}
		// After successful connection, check if reports need to be generated for previous month
		if (conn != null && ret.contains("SQL connection succeed")) {
			try {
				generateMonthlyReportsIfNeeded();
				// Start the scheduled task to check for missing reports monthly
				startMonthlyReportScheduler();
			} catch (Exception e) {
				System.err.println("⚠️ Warning: Failed to generate monthly reports on startup: " + e.getMessage());
			}
		}
		
		return ret;
		
	}
	
	/**
	 * Automatically generates reports for all missing months.
	 * This method checks from the earliest month with order data up to the previous month,
	 * ensuring all missed reports are generated (e.g., if server was down during scheduled times).
	 * This should be called at server startup and can be scheduled to run monthly.
	 */
	public static void generateMonthlyReportsIfNeeded() {
		if (conn == null) {
			System.err.println("❌ Cannot generate monthly reports - database connection is null");
			return;
		}
		
		try {
			// Find the earliest order date to determine how far back to check
			java.time.YearMonth earliestMonth = findEarliestMonthWithOrders();
			if (earliestMonth == null) {
				System.out.println("ℹ️ No orders found in database, skipping report generation");
				return;
			}
			
			// Calculate the previous month (last month that should have reports)
			java.time.LocalDate now = java.time.LocalDate.now();
			java.time.LocalDate firstDayOfCurrentMonth = now.withDayOfMonth(1);
			java.time.LocalDate lastDayOfPreviousMonth = firstDayOfCurrentMonth.minusDays(1);
			java.time.LocalDate firstDayOfPreviousMonth = lastDayOfPreviousMonth.withDayOfMonth(1);
			java.time.YearMonth previousMonth = java.time.YearMonth.from(firstDayOfPreviousMonth);
			
			System.out.println("🔍 Checking for missing reports from " + earliestMonth + " to " + previousMonth);
			
			// Check each month from earliest to previous month
			int generatedCount = 0;
			java.time.YearMonth currentCheck = earliestMonth;
			
			while (!currentCheck.isAfter(previousMonth)) {
				List<java.time.YearMonth> months = new ArrayList<>();
				months.add(currentCheck);
				String monthsJson = monthsToJson(months);
				
				// Check if delay report exists
				String delayReport = findSavedReport("delay", monthsJson);
				if (delayReport == null) {
					System.out.println("📊 Auto-generating delay report for " + currentCheck);
					generateReportForMonth(currentCheck, "delay");
					generatedCount++;
				}
				
				// Check if reservation report exists
				String reservationReport = findSavedReport("reservation", monthsJson);
				if (reservationReport == null) {
					System.out.println("📊 Auto-generating reservation report for " + currentCheck);
					generateReportForMonth(currentCheck, "reservation");
					generatedCount++;
				}
				
				// Move to next month
				currentCheck = currentCheck.plusMonths(1);
			}
			
			if (generatedCount > 0) {
				System.out.println("✅ Generated " + generatedCount + " missing report(s)");
			} else {
				System.out.println("ℹ️ All reports are up to date");
			}
			
		} catch (Exception e) {
			System.err.println("❌ Error generating monthly reports: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Finds the earliest month that has order data in the database.
	 * @return YearMonth of the earliest order, or null if no orders exist
	 */
	private static java.time.YearMonth findEarliestMonthWithOrders() {
		try {
			String sql = "SELECT MIN(order_time_date) as earliest_date FROM orders";
			try (PreparedStatement pstmt = conn.prepareStatement(sql);
				 ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					java.sql.Timestamp earliestTimestamp = rs.getTimestamp("earliest_date");
					if (earliestTimestamp != null) {
						java.time.LocalDateTime earliestDateTime = earliestTimestamp.toLocalDateTime();
						return java.time.YearMonth.from(earliestDateTime);
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error finding earliest month with orders: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Generates a report for a specific month and report type.
	 * @param month The month to generate the report for
	 * @param reportType "delay" or "reservation"
	 */
	private static void generateReportForMonth(java.time.YearMonth month, String reportType) {
		try {
			if ("delay".equals(reportType)) {
				// Build month string in the format expected by GetDelayChartReport
				String monthsStr = month.toString();
				GetDelayChartReport(monthsStr);
			} else if ("reservation".equals(reportType)) {
				String monthsStr = month.toString();
				GetReservationChartReport(monthsStr);
			}
		} catch (Exception e) {
			System.err.println("❌ Error generating " + reportType + " report for " + month + ": " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private static boolean shutdownHookRegistered = false;
	
	/**
	 * Starts a scheduled task that runs monthly on the 1st of each month at 2:00 AM
	 * to automatically generate reports for the previous month if they don't exist.
	 * This ensures reports are generated even if the server runs for multiple months 
	 * without restarting, and is more efficient than checking daily.
	 * 
	 * After each run, it schedules the next check for the 1st of the following month.
	 */
	private static void startMonthlyReportScheduler() {
		// Don't start if already running
		if (reportScheduler != null && !reportScheduler.isShutdown()) {
			System.out.println("ℹ️ Monthly report scheduler is already running.");
			return;
		}
		
		// Register shutdown hook only once
		if (!shutdownHookRegistered) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				stopMonthlyReportScheduler();
			}, "ReportSchedulerShutdownHook"));
			shutdownHookRegistered = true;
		}
		
		reportScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "MonthlyReportScheduler");
			t.setDaemon(true); // Don't prevent JVM shutdown
			return t;
		});
		
		// Schedule the first monthly check
		scheduleNextMonthlyCheck();
	}
	
	/**
	 * Schedules the next monthly report check for the 1st of the next month at 2:00 AM.
	 * After the check runs, it will automatically schedule the next one for the following month.
	 */
	private static void scheduleNextMonthlyCheck() {
		if (reportScheduler == null || reportScheduler.isShutdown()) {
			return;
		}
		
		// Calculate the 1st of next month at 2:00 AM
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		
		// Get the 1st of the current month at 2:00 AM
		java.time.LocalDateTime firstOfCurrentMonth = now
			.withDayOfMonth(1)
			.withHour(2)
			.withMinute(0)
			.withSecond(0)
			.withNano(0);
		
		java.time.LocalDateTime firstOfNextMonth;
		
		// If we're before or at the 1st at 2:00 AM, schedule for next month
		// If we're past the 1st at 2:00 AM, also schedule for next month
		if (now.isBefore(firstOfCurrentMonth) || now.isEqual(firstOfCurrentMonth)) {
			// We're before the 1st at 2 AM, so schedule for the 1st of this month
			firstOfNextMonth = firstOfCurrentMonth;
		} else {
			// We're past the 1st at 2 AM, so schedule for the 1st of next month
			firstOfNextMonth = firstOfCurrentMonth.plusMonths(1);
		}
		
		long delayMinutes = java.time.Duration.between(now, firstOfNextMonth).toMinutes();
		
		// Schedule the task to run once at the calculated time
		reportScheduler.schedule(() -> {
			try {
				System.out.println("🕐 Scheduled monthly report check running on " + 
					java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
				generateMonthlyReportsIfNeeded();
				
				// After running, schedule the next check for the 1st of the following month
				scheduleNextMonthlyCheck();
			} catch (Exception e) {
				System.err.println("❌ Error in scheduled monthly report generation: " + e.getMessage());
				e.printStackTrace();
				// Still schedule the next check even if this one failed
				scheduleNextMonthlyCheck();
			}
		}, delayMinutes, TimeUnit.MINUTES);
		
		java.time.Duration duration = java.time.Duration.ofMinutes(delayMinutes);
		long days = duration.toDays();
		long hours = duration.toHours() % 24;
		long minutes = duration.toMinutes() % 60;
		
		System.out.println("✅ Monthly report scheduler started. Next check on " + 
			firstOfNextMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' HH:mm")) +
			" (in approximately " + days + " days, " + hours + " hours, " + minutes + " minutes)");
	}
	
	/**
	 * Stops the monthly report scheduler. Should be called when shutting down the server.
	 */
	public static void stopMonthlyReportScheduler() {
		if (reportScheduler != null && !reportScheduler.isShutdown()) {
			System.out.println("🛑 Stopping monthly report scheduler...");
			reportScheduler.shutdown();
			try {
				if (!reportScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
					reportScheduler.shutdownNow();
				}
			} catch (InterruptedException e) {
				reportScheduler.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
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
			stmt = conn.prepareStatement("UPDATE orders SET number_of_guests = \"" + num_of_guests
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
				    "UPDATE orders SET order_time_date = ? WHERE order_number = ?"
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
				    "UPDATE orders SET status = ? WHERE confirmation_code = ?"
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
		String query = "SELECT * FROM orders WHERE  order_number = \"" + order_num + "\"";
		String orderData = new String("Empty");
		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			while (rs.next()) {
				String order_num1 = rs.getString("order_number");
				Date date = rs.getTimestamp("order_time_date") != null ? new Date(rs.getTimestamp("order_time_date").getTime()) : null;
				int num_guests = rs.getInt("number_of_guests");
				String con_code = rs.getString("confirmation_code");
				Integer sub_id = rs.getObject("subscriber_id") != null ? rs.getInt("subscriber_id") : null;
				Date date_placing_order = rs.getTimestamp("time_date_of_placing_order") != null ? new Date(rs.getTimestamp("time_date_of_placing_order").getTime()) : null;

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
		String query = "SELECT * FROM orders";

		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			while (rs.next()) {
				String order_num1 = rs.getString("order_number");
				Date date = rs.getTimestamp("order_time_date") != null ? new Date(rs.getTimestamp("order_time_date").getTime()) : null;
				int num_guests = rs.getInt("number_of_guests");
				String con_code = rs.getString("confirmation_code");
				Integer sub_id = rs.getObject("subscriber_id") != null ? rs.getInt("subscriber_id") : null;
				Date date_placing_order = rs.getTimestamp("time_date_of_placing_order") != null ? new Date(rs.getTimestamp("time_date_of_placing_order").getTime()) : null;

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

	/**
	 * This method inserts a new subscriber into the subscriber table
	 * @param fullName - subscriber's full name (first name and last name)
	 * @param phone - subscriber's phone number (used as subscriberID)
	 * @param subscriberId - subscriber ID (same as phone number)
	 * @param email - subscriber's email address
	 * @return "SubscriberAdded" if successful, "SubscriberExists" if phone already exists, "Error" on failure
	 */
	public static String insertSubscriber(String fullName, String phone, String subscriberId, String email) {
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Error: Database not connected";
		}
		
		// Match actual table schema: subscriberID, name, email, phone, created_at
		// Use NOW() to insert current date and time (includes hours, minutes, seconds)
		String sql = "INSERT INTO subscriber (subscriberID, name, email, phone, created_at) VALUES (?, ?, ?, ?, NOW())";
		
		try {
			// Check if subscriber with this phone number already exists
			String checkSql = "SELECT subscriberID FROM subscriber WHERE phone = ? OR subscriberID = ?";
			try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
				checkStmt.setString(1, phone);
				checkStmt.setString(2, subscriberId);
				ResultSet rs = checkStmt.executeQuery();
				if (rs.next()) {
					return "SubscriberExists";
				}
			}
			
			// Insert new subscriber - using NOW() to store current date and time
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setString(1, subscriberId);
				pstmt.setString(2, fullName.trim());
				pstmt.setString(3, email);
				pstmt.setString(4, phone);
				// created_at is set using NOW() in SQL - no need for setDate parameter
				
				int rowsAffected = pstmt.executeUpdate();
				if (rowsAffected > 0) {
					return "SubscriberAdded";
				} else {
					return "Error: No rows inserted";
				}
			}
			
		} catch (SQLException e) {
			System.err.println("❌ Error inserting subscriber");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			
			// Check if error is due to duplicate entry
			if (e.getSQLState() != null && e.getSQLState().equals("23000")) { // Integrity constraint violation
				return "SubscriberExists";
			}
			// Return more detailed error for debugging
			return "Error: " + e.getMessage();
		} catch (Exception e) {
			System.err.println("❌ Unexpected error inserting subscriber");
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * This method updates opening hours in the opening_hours table
	 * @param openingTime - opening time in HH:mm format
	 * @param closingTime - closing time in HH:mm format
	 * @param durationType - "SPECIFIC_DAY", "NUMBER_OF_DAYS", or "PERMANENT"
	 * @param durationValue - "dayOfWeek|date" for SPECIFIC_DAY, number of days for NUMBER_OF_DAYS, comma-separated days for PERMANENT
	 * @return "OpeningHoursUpdated" if successful, "Error" on failure
	 */
	public static String updateOpeningHours(String openingTime, String closingTime, String durationType, String durationValue) {
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Error: Database not connected";
		}

		try {
			if (durationType.equals("SPECIFIC_DAY")) {
				// Update for specific day and date
				// durationValue format: "dayOfWeek|date" (e.g., "Sunday|2024-01-15")
				String[] parts = durationValue.split("\\|");
				if (parts.length != 2) {
					return "Error: Invalid format for specific day. Expected 'dayOfWeek|date'";
				}
				String dayOfWeek = parts[0].trim();
				String dateStr = parts[1].trim();
				
				String sql = "INSERT INTO opening_hours (day_of_week, opening_time, closing_time, specific_date, is_active, is_permanent) " +
				             "VALUES (?, ?, ?, ?, true, false) " +
				             "ON DUPLICATE KEY UPDATE opening_time = ?, closing_time = ?, is_active = true, is_permanent = false";
				
				try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
					pstmt.setString(1, dayOfWeek);
					pstmt.setString(2, openingTime);
					pstmt.setString(3, closingTime);
					pstmt.setDate(4, Date.valueOf(dateStr));
					pstmt.setString(5, openingTime);
					pstmt.setString(6, closingTime);
					pstmt.executeUpdate();
				}
			} else if (durationType.equals("NUMBER_OF_DAYS")) {
				// Update for number of days starting from tomorrow
				int numberOfDays = Integer.parseInt(durationValue);
				LocalDate startDate = LocalDate.now().plusDays(1); // Start from tomorrow
				LocalDate endDate = startDate.plusDays(numberOfDays - 1); // End date (inclusive)
				
				LocalDate currentDate = startDate;
				while (!currentDate.isAfter(endDate)) {
					// Get day of week for current date
					String currentDayOfWeek = currentDate.getDayOfWeek().toString();
					// Convert Java DayOfWeek to our day format (e.g., MONDAY -> Monday)
					String dayFormatted = currentDayOfWeek.charAt(0) + currentDayOfWeek.substring(1).toLowerCase();
					
					// Insert opening hours for this specific date
					String sql = "INSERT INTO opening_hours (day_of_week, opening_time, closing_time, specific_date, is_active, is_permanent) " +
					             "VALUES (?, ?, ?, ?, true, false) " +
					             "ON DUPLICATE KEY UPDATE opening_time = ?, closing_time = ?, is_active = true, is_permanent = false";
					
					try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
						pstmt.setString(1, dayFormatted);
						pstmt.setString(2, openingTime);
						pstmt.setString(3, closingTime);
						pstmt.setDate(4, Date.valueOf(currentDate));
						pstmt.setString(5, openingTime);
						pstmt.setString(6, closingTime);
						pstmt.executeUpdate();
					}
					currentDate = currentDate.plusDays(1);
				}
			} else if (durationType.equals("PERMANENT")) {
				// Update permanently - set default hours for selected days
				// durationValue contains comma-separated list of days (e.g., "Sunday,Monday,Wednesday")
				String[] selectedDays = durationValue.split(",");
				
				for (String day : selectedDays) {
					day = day.trim();
					if (!day.isEmpty()) {
						String sql = "INSERT INTO opening_hours (day_of_week, opening_time, closing_time, is_active, is_permanent) " +
						             "VALUES (?, ?, ?, true, true) " +
						             "ON DUPLICATE KEY UPDATE opening_time = ?, closing_time = ?, is_active = true, is_permanent = true";
						
						try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
							pstmt.setString(1, day);
							pstmt.setString(2, openingTime);
							pstmt.setString(3, closingTime);
							pstmt.setString(4, openingTime);
							pstmt.setString(5, closingTime);
							pstmt.executeUpdate();
						}
					}
				}
			}
			
			return "OpeningHoursUpdated";
			
		} catch (SQLException e) {
			System.err.println("❌ Error updating opening hours");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			return "Error: " + e.getMessage();
		} catch (Exception e) {
			System.err.println("❌ Unexpected error updating opening hours");
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Get the next available table ID (highest tableID + 1)
	 * @return String representation of the next table ID, or "1" if no tables exist
	 */
	public static String getNextTableId() {
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Error: Database not connected";
		}

		try {
			String sql = "SELECT COALESCE(MAX(tableID), 0) + 1 AS nextId FROM tables";
			try (PreparedStatement pstmt = conn.prepareStatement(sql);
			     ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return String.valueOf(rs.getInt("nextId"));
				}
			}
			return "1"; // Default if no tables exist
		} catch (SQLException e) {
			System.err.println("❌ Error getting next table ID");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Insert a new table into the tables table
	 * @param capacity - number of chairs/seats
	 * @return "TableAdded" if successful, "Error" on failure
	 */
	public static String insertTable(int capacity) {
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Error: Database not connected";
		}

		try {
			// Insert new table - tableID is auto-increment, tableStatus defaults to 'Available'
			String sql = "INSERT INTO tables (capacity) VALUES (?)";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, capacity);
				
				int rowsAffected = pstmt.executeUpdate();
				if (rowsAffected > 0) {
					return "TableAdded";
				} else {
					return "Error: No rows inserted";
				}
			}
			
		} catch (SQLException e) {
			System.err.println("❌ Error inserting table");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			return "Error: " + e.getMessage();
		} catch (Exception e) {
			System.err.println("❌ Unexpected error inserting table");
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Get all table IDs from the tables table
	 * @return Comma-separated string of table IDs, or "Error" on failure
	 */
	public static String getAllTableIds() {
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Error: Database not connected";
		}

		try {
			String sql = "SELECT tableID FROM tables ORDER BY tableID";
			List<String> tableIds = new ArrayList<>();
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql);
			     ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					tableIds.add(String.valueOf(rs.getInt("tableID")));
				}
			}
			
			return String.join(",", tableIds);
		} catch (SQLException e) {
			System.err.println("❌ Error getting all table IDs");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Get table data (capacity) for a specific table ID
	 * @param tableId - table ID to get data for
	 * @return Capacity as string, or "Error" on failure
	 */
	public static String getTableData(String tableId) {
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Error: Database not connected";
		}

		try {
			String sql = "SELECT capacity FROM tables WHERE tableID = ?";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, Integer.parseInt(tableId));
				
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						return String.valueOf(rs.getInt("capacity"));
					} else {
						return "Error: Table not found";
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error getting table data");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			return "Error: " + e.getMessage();
		} catch (NumberFormatException e) {
			return "Error: Invalid table ID format";
		}
	}

	/**
	 * Update table capacity
	 * @param tableId - table ID to update
	 * @param capacity - new capacity value
	 * @return "TableUpdated" if successful, "Error" on failure
	 */
	public static String updateTable(int tableId, int capacity) {
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Error: Database not connected";
		}

		try {
			String sql = "UPDATE tables SET capacity = ? WHERE tableID = ?";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, capacity);
				pstmt.setInt(2, tableId);
				
				int rowsAffected = pstmt.executeUpdate();
				if (rowsAffected > 0) {
					return "TableUpdated";
				} else {
					return "Error: Table not found or no changes made";
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error updating table");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Delete a table from the database
	 * @param tableId - table ID to delete
	 * @return "TableDeleted" if successful, "Error" on failure
	 */
	public static String deleteTable(int tableId) {
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Error: Database not connected";
		}

		try {
			String sql = "DELETE FROM tables WHERE tableID = ?";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, tableId);
				
				int rowsAffected = pstmt.executeUpdate();
				if (rowsAffected > 0) {
					return "TableDeleted";
				} else {
					return "Error: Table not found";
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error deleting table");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Get all tables with status 'OCCUPIED' (occupied tables)
	 * Joins with orders table to get customerName (name) and checkInTime (order_time_date) based on confirmationCode
	 * @return List of occupied tables as strings (format: "tableID, capacity, customerName, checkInTime, confirmationCode")
	 */
	public static List<String> GetOccupiedTables() {
		List<String> occupiedTables = new ArrayList<>();
		
		// Check if connection is null
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return occupiedTables; // Return empty list
		}

		try {
			// Join with orders table to get name (customerName) and visit table to get startTime (checkInTime)
			// Joined on confirmationCode
			String sql = "SELECT t.tableID, t.capacity, o.name AS customerName, v.startTime AS checkInTime, t.confirmationCode, v.is_reserved, v.is_late, v.billID " +
			             "FROM tables t " +
			             "LEFT JOIN orders o ON t.confirmationCode = o.confirmation_code " +
			             "LEFT JOIN visit v ON t.confirmationCode = v.confirmation_code " +
			             "WHERE t.tableStatus = 'OCCUPIED' " +
			             "ORDER BY t.tableID";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql);
			     ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					int tableId = rs.getInt("tableID");
					int cap = rs.getInt("capacity");
					String customerName = rs.getString("customerName");
					java.sql.Timestamp checkInTime = rs.getTimestamp("checkInTime");
					String confirmationCode = rs.getString("confirmationCode");
					
					// Handle NULL values
					if (customerName == null) {
						customerName = "";
					}
					String checkInTimeStr = "";
					if (checkInTime != null) {
						checkInTimeStr = checkInTime.toString();
					}
					if (confirmationCode == null) {
						confirmationCode = "";
					}
					
					// Create a formatted string: "tableID, capacity, customerName, checkInTime, confirmationCode"
					String tableData = tableId + ", " + cap + ", " + customerName + ", " + checkInTimeStr + ", " + confirmationCode;
					occupiedTables.add(tableData);
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error getting occupied tables");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
		}
		
		return occupiedTables;
	}

	/**
	 * Get waiting list from waitingentry table
	 * @return List of waiting entries as strings (format: "waitingID, number_of_guests, phone, date, status, created_at")
	 */
	public static List<String> GetWaitingList() {
		List<String> waitingList = new ArrayList<>();
		
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return waitingList;
		}

		try {
			String sql = "SELECT waitingID, number_of_guests, phone, date, status, created_at FROM waitingentry ORDER BY waitingID";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql);
			     ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					int waitingID = rs.getInt("waitingID");
					int number_of_guests = rs.getInt("number_of_guests");
					String phone = rs.getString("phone");
					Date date = rs.getDate("date");
					String status = rs.getString("status");
					Timestamp created_at = rs.getTimestamp("created_at");
					
					// Handle NULL values
					if (phone == null) phone = "";
					if (status == null) status = "";
					String dateStr = "";
					if (date != null) {
						dateStr = date.toString();
					}
					String created_atStr = "";
					if (created_at != null) {
						created_atStr = created_at.toString();
					}
					
					String waitingEntry = waitingID + ", " + number_of_guests + ", " + phone + ", " + dateStr + ", " + status + ", " + created_atStr;
					waitingList.add(waitingEntry);
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error getting waiting list");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
		}
		
		return waitingList;
	}

	/**
	 * Get current reservations from orders table for today
	 * @return List of current reservations as strings (format: "order_number, confirmation_code, name, phone, status, order_time_date")
	 */
	public static List<String> GetCurrentReservations() {
		List<String> currentReservations = new ArrayList<>();
		
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return currentReservations;
		}

		try {
			// Get reservations for today where order_time_date >= NOW() and DATE(order_time_date) = CURDATE()
			String sql = "SELECT order_number, confirmation_code, name, phone, status, order_time_date " +
			             "FROM orders " +
			             "WHERE order_time_date >= NOW() " +
			             "AND DATE(order_time_date) = CURDATE() " +
			             "ORDER BY order_time_date ASC";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql);
			     ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String order_number = rs.getString("order_number");
					String confirmation_code = rs.getString("confirmation_code");
					String name = rs.getString("name");
					String phone = rs.getString("phone");
					String status = rs.getString("status");
					Timestamp order_time_date = rs.getTimestamp("order_time_date");
					
					// Handle NULL values
					if (order_number == null) order_number = "";
					if (confirmation_code == null) confirmation_code = "";
					if (name == null) name = "";
					if (phone == null) phone = "";
					if (status == null) status = "";
					String order_time_dateStr = "";
					if (order_time_date != null) {
						order_time_dateStr = order_time_date.toString();
					}
					
					String reservation = order_number + ", " + confirmation_code + ", " + name + ", " + phone + ", " + status + ", " + order_time_dateStr;
					currentReservations.add(reservation);
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error getting current reservations");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
		}
		
		return currentReservations;
	}

	/**
	 * Get subscriber information from subscriber table
	 * @return List of subscribers as strings (format: "subscriberID, name, email, phone")
	 */
	public static List<String> GetSubInfo() {
		List<String> subInfo = new ArrayList<>();
		
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return subInfo;
		}

		try {
			String sql = "SELECT subscriberID, name, email, phone FROM subscriber ORDER BY subscriberID";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql);
			     ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String subscriberID = rs.getString("subscriberID");
					String name = rs.getString("name");
					String email = rs.getString("email");
					String phone = rs.getString("phone");
					
					// Handle NULL values
					if (subscriberID == null) subscriberID = "";
					if (name == null) name = "";
					if (email == null) email = "";
					if (phone == null) phone = "";
					
					String subscriber = subscriberID + ", " + name + ", " + email + ", " + phone;
					subInfo.add(subscriber);
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error getting subscriber info");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
		}
		
		return subInfo;
	}

	/**
	 * Validates a user ID by checking if it exists in subscriber, staffids, or managerids tables
	 * @param userID - the user ID to validate
	 * @return "Subscriber" if found in subscriber table, "Staff" if found in staffids, "Manager" if found in managerids, "Invalid" otherwise
	 */
	public static String validateUserID(String userID) {
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return "Invalid";
		}

		try {
			// Check subscriber table
			String subscriberSql = "SELECT subscriberID FROM subscriber WHERE subscriberID = ?";
			try (PreparedStatement pstmt = conn.prepareStatement(subscriberSql)) {
				pstmt.setString(1, userID);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						return "Subscriber";
					}
				}
			}

			// Check staffids table
			String staffSql = "SELECT staffID FROM staffids WHERE staffID = ?";
			try (PreparedStatement pstmt = conn.prepareStatement(staffSql)) {
				pstmt.setString(1, userID);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						return "Staff";
					}
				}
			}

			// Check managerids table
			String managerSql = "SELECT managerID FROM managerids WHERE managerID = ?";
			try (PreparedStatement pstmt = conn.prepareStatement(managerSql)) {
				pstmt.setString(1, userID);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						return "Manager";
					}
				}
			}

			// ID not found in any table
			return "Invalid";

		} catch (SQLException e) {
			System.err.println("❌ Error validating user ID");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			return "Invalid";
		}
	}

	/**
	 * sorting months,and returning a JSON string like ["2025-07","2025-11"]
	 */
	private static String monthsToJson(List<java.time.YearMonth> months) {
		if (months == null) {
			return "[]";
		}
		Collections.sort(months);
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < months.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append("\"").append(months.get(i).toString()).append("\"");
		}
		sb.append("]");
		return sb.toString();
	}
	
	/**
	 * returns Timestamp at start of the month AFTER the last month in the report period
	 * For example, if report is for November 2025, returns December 1, 2025 00:00:00
	 */
	private static java.sql.Timestamp firstMonthStart(List<java.time.YearMonth> months) {
		Collections.sort(months);
		// Get the last month in the list and add 1 month to get the creation date
		java.time.YearMonth lastMonth = months.get(months.size() - 1);
		java.time.YearMonth creationMonth = lastMonth.plusMonths(1);
		return java.sql.Timestamp.valueOf(creationMonth.atDay(1).atStartOfDay());
	}
	
	/**
	 * Trying to load a saved report from report_history
	 */
	private static String findSavedReport(String reportType, String monthsJson) {
		String sql = "SELECT payload FROM report_history WHERE report_type = ? AND months = ? ORDER BY created_at DESC LIMIT 1";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, reportType);
			pstmt.setString(2, monthsJson);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return rs.getString("payload");
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error reading report_history");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
		}
		return null;
	}
	
	/**
	 * Helper: save a generated report into report_history
	 * This method is called after generating a report, so it should always save (unless there's a duplicate key)
	 */
	private static void saveReport(String reportType, String monthsJson, String periodLabel, String payload, java.sql.Timestamp createdAt) {
		if (conn == null) {
			System.err.println("❌ Cannot save report - database connection is null");
			return;
		}
		
		System.out.println("💾 Attempting to save report: type=" + reportType + ", months=" + monthsJson + ", created_at=" + createdAt);
		
		// First, check if report already exists to prevent duplicates
		String existingReport = findSavedReport(reportType, monthsJson);
		if (existingReport != null) {
			System.out.println("ℹ️ Report already exists in database, skipping save to prevent duplicate (" + reportType + ", " + monthsJson + ")");
			return;
		}
		
		// Use INSERT IGNORE as additional protection (in case of race conditions)
		String sql = "INSERT IGNORE INTO report_history (report_type, months, period_label, payload, created_at) VALUES (?, ?, ?, ?, ?)";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, reportType);
			pstmt.setString(2, monthsJson);
			pstmt.setString(3, periodLabel);
			pstmt.setString(4, payload);
			pstmt.setTimestamp(5, createdAt);
			int rowsAffected = pstmt.executeUpdate();
			if (rowsAffected > 0) {
				System.out.println("✅ Successfully saved report to report_history (" + reportType + ", " + monthsJson + ", created_at: " + createdAt + ")");
			} else {
				System.err.println("⚠️ Failed to save report - no rows affected (may be duplicate or constraint violation) - " + reportType + ", " + monthsJson);
			}
		} catch (SQLException e) {
			System.err.println("❌ Error saving report_history");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Get delay chart report for custom months.
	 * @param customMonths Comma-separated list of months in "YYYY-MM" format (e.g., "2025-01,2025-02,2024-07")
	 * @return List with single string containing all statistics separated by |
	 */
	public static List<String> GetDelayChartReport(String customMonths) {
		List<String> reportData = new ArrayList<>();
		
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			String emptyReport = "NO_DATA|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(emptyReport);
			return reportData;
		}
		
		try {
			List<java.time.YearMonth> months = new ArrayList<>();
			
			if (customMonths != null && !customMonths.isEmpty()) {
				// Parse custom months
				String[] monthStrings = customMonths.split(",");
				for (String monthStr : monthStrings) {
					try {
						java.time.YearMonth ym = java.time.YearMonth.parse(monthStr.trim());
						months.add(ym);
					} catch (Exception e) {
						System.err.println("❌ Invalid month format: " + monthStr);
					}
				}
			}
			
			if (months.isEmpty()) {
				// Default: previous month
				java.time.LocalDate now = java.time.LocalDate.now();
				java.time.LocalDate firstDayOfCurrentMonth = now.withDayOfMonth(1);
				java.time.LocalDate lastDayOfPreviousMonth = firstDayOfCurrentMonth.minusDays(1);
				java.time.LocalDate firstDayOfPreviousMonth = lastDayOfPreviousMonth.withDayOfMonth(1);
				months.add(java.time.YearMonth.from(firstDayOfPreviousMonth));
			}
			
			// sorting the months
			Collections.sort(months);
			String monthsJson = monthsToJson(months);
			
			// Try to return a saved report first
			String cached = findSavedReport("delay", monthsJson);
			if (cached != null) {
				System.out.println("💾 Using cached delay report for " + monthsJson);
				reportData.add(cached);
				return reportData;
			}
			
			// Build report period string
			StringBuilder reportPeriod = new StringBuilder();
			for (int i = 0; i < months.size(); i++) {
				if (i > 0) reportPeriod.append(" + ");
				reportPeriod.append(months.get(i).getMonth().toString()).append(" ").append(months.get(i).getYear());
			}
			
			System.out.println("📊 Generating Delay Chart Report for: " + reportPeriod.toString());
			
			// Build WHERE clause for multiple months
			StringBuilder whereClause = new StringBuilder("(");
			for (int i = 0; i < months.size(); i++) {
				if (i > 0) whereClause.append(" OR ");
				whereClause.append("(o.order_time_date >= ? AND o.order_time_date < ?)");
			}
			whereClause.append(")");
			
			String sql = "SELECT o.order_time_date, v.startTime, v.endTime, o.status, o.confirmation_code, v.is_reserved, v.is_late, v.billID " +
			             "FROM orders o " +
			             "LEFT JOIN visit v ON o.confirmation_code = v.confirmation_code " +
			             "WHERE " + whereClause.toString() + " " +
			             "ORDER BY o.order_time_date";
			
			int totalClients = 0;
			int onTimeCount = 0;
			int late1to14Count = 0;
			int late15PlusCount = 0;
			int cancelledCount = 0;
			int mealUnder2HrCount = 0;
			int mealOver2HrCount = 0;
			int clientsWithMealDuration = 0;
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				// Set parameters for each month
				int paramIndex = 1;
				for (java.time.YearMonth ym : months) {
					java.sql.Timestamp monthStart = java.sql.Timestamp.valueOf(ym.atDay(1).atStartOfDay());
					java.sql.Timestamp monthEnd = java.sql.Timestamp.valueOf(ym.plusMonths(1).atDay(1).atStartOfDay());
					pstmt.setTimestamp(paramIndex++, monthStart);
					pstmt.setTimestamp(paramIndex++, monthEnd);
					System.out.println("   Month: " + ym + " (" + monthStart + " to " + monthEnd + ")");
				}
				
				try (ResultSet rs = pstmt.executeQuery()) {
					int rowCount = 0;
					while (rs.next()) {
						rowCount++;
						totalClients++;
						
						java.sql.Timestamp orderTime = rs.getTimestamp("order_time_date");
						java.sql.Timestamp startTime = rs.getTimestamp("startTime");
						java.sql.Timestamp endTime = rs.getTimestamp("endTime");
						String status = rs.getString("status");
						
						boolean isCancelled = "cancelled by restaurant".equalsIgnoreCase(status) || 
						                      "Cancelled by restaurant".equalsIgnoreCase(status);
						
						if (orderTime != null && startTime != null) {
							long delayMinutes = java.time.Duration.between(
								orderTime.toInstant(), 
								startTime.toInstant()
							).toMinutes();
							
							if (delayMinutes == 0) {
								onTimeCount++;
							} else if (delayMinutes >= 1 && delayMinutes <= 14) {
								late1to14Count++;
							} else if (delayMinutes > 14) {
								late15PlusCount++;
								if (isCancelled) {
									cancelledCount++;
								}
							}
						} else if (isCancelled) {
							late15PlusCount++;
							cancelledCount++;
						}
						
						if (startTime != null && endTime != null) {
							clientsWithMealDuration++;
							long durationHours = java.time.Duration.between(
								startTime.toInstant(), 
								endTime.toInstant()
							).toHours();
							
							if (durationHours < 2) {
								mealUnder2HrCount++;
							} else {
								mealOver2HrCount++;
							}
						}
					}
					System.out.println("📊 Found " + rowCount + " orders in selected months");
				}
			}
			
			// Calculate percentages
			double onTimePercent = totalClients > 0 ? (onTimeCount * 100.0 / totalClients) : 0.0;
			double late1to14Percent = totalClients > 0 ? (late1to14Count * 100.0 / totalClients) : 0.0;
			double late15PlusPercent = totalClients > 0 ? (late15PlusCount * 100.0 / totalClients) : 0.0;
			double cancelledPercent = totalClients > 0 ? (cancelledCount * 100.0 / totalClients) : 0.0;
			double mealUnder2HrPercent = clientsWithMealDuration > 0 ? (mealUnder2HrCount * 100.0 / clientsWithMealDuration) : 0.0;
			double mealOver2HrPercent = clientsWithMealDuration > 0 ? (mealOver2HrCount * 100.0 / clientsWithMealDuration) : 0.0;
			
			String reportString = String.format("%s|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d",
				reportPeriod.toString(),
				onTimeCount, onTimePercent,
				late1to14Count, late1to14Percent,
				late15PlusCount, late15PlusPercent,
				cancelledCount, cancelledPercent,
				mealUnder2HrCount, mealUnder2HrPercent,
				mealOver2HrCount, mealOver2HrPercent,
				totalClients
			);
			
			System.out.println("📊 Report Statistics:");
			System.out.println("   Total Clients: " + totalClients);
			System.out.println("   On Time: " + onTimeCount + " (" + String.format("%.1f", onTimePercent) + "%)");
			System.out.println("   Late 1-14 mins: " + late1to14Count + " (" + String.format("%.1f", late1to14Percent) + "%)");
			System.out.println("   Late >15 mins: " + late15PlusCount + " (" + String.format("%.1f", late15PlusPercent) + "%)");
			System.out.println("   Cancelled: " + cancelledCount + " (" + String.format("%.1f", cancelledPercent) + "%)");
			System.out.println("   Meal <2hrs: " + mealUnder2HrCount + " (" + String.format("%.1f", mealUnder2HrPercent) + "%)");
			System.out.println("   Meal >2hrs: " + mealOver2HrCount + " (" + String.format("%.1f", mealOver2HrPercent) + "%)");
			
			// saving the report for future retrieval
			System.out.println("💾 Preparing to save delay report...");
			try {
				java.sql.Timestamp createdAt = firstMonthStart(months);
				System.out.println("💾 Created timestamp: " + createdAt);
				System.out.println("💾 Months JSON: " + monthsToJson(months));
				saveReport("delay", monthsToJson(months), reportPeriod.toString(), reportString, createdAt);
			} catch (Exception e) {
				System.err.println("⚠️ Failed to save delay report: " + e.getMessage());
				e.printStackTrace();
			}
			
			reportData.add(reportString);
			System.out.println("✅ Report string added to list. List size: " + reportData.size());
			
		} catch (SQLException e) {
			System.err.println("❌ Error getting delay chart report");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			String errorReport = "ERROR|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(errorReport);
		} catch (Exception e) {
			System.err.println("❌ Unexpected error in GetDelayChartReport: " + e.getMessage());
			e.printStackTrace();
			String errorReport = "ERROR|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(errorReport);
		}
		
		System.out.println("📤 Returning report data. List size: " + reportData.size());
		return reportData;
	}
	
	/**
	 * Gets delay chart report data for the previous month (default).
	 * Calculates arrival timing and meal duration statistics.
	 * @return List with single string containing all statistics separated by |
	 * Format: "reportPeriod|onTimeCount|onTimePercent|late1to14Count|late1to14Percent|late15PlusCount|late15PlusPercent|cancelledCount|cancelledPercent|mealUnder2HrCount|mealUnder2HrPercent|mealOver2HrCount|mealOver2HrPercent|totalClients"
	 */
	public static List<String> GetDelayChartReport() {
		List<String> reportData = new ArrayList<>();
		
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			System.err.println("❌ Cannot generate delay chart report - no database connection");
			// Still return a report with zeros so client knows the request was processed
			String emptyReport = "NO_DATA|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(emptyReport);
			return reportData;
		}
		
		try {
			// Calculate previous month date range
			java.time.LocalDate now = java.time.LocalDate.now();
			java.time.LocalDate firstDayOfCurrentMonth = now.withDayOfMonth(1);
			java.time.LocalDate lastDayOfPreviousMonth = firstDayOfCurrentMonth.minusDays(1);
			java.time.LocalDate firstDayOfPreviousMonth = lastDayOfPreviousMonth.withDayOfMonth(1);
			
			java.sql.Timestamp startOfPreviousMonth = java.sql.Timestamp.valueOf(firstDayOfPreviousMonth.atStartOfDay());
			java.sql.Timestamp endOfPreviousMonth = java.sql.Timestamp.valueOf(firstDayOfPreviousMonth.plusMonths(1).atStartOfDay());
			
			String reportPeriod = firstDayOfPreviousMonth.getMonth().toString() + " " + firstDayOfPreviousMonth.getYear();
			
			System.out.println("📊 Generating Delay Chart Report for: " + reportPeriod);
			System.out.println("📅 Date range: " + startOfPreviousMonth + " to " + endOfPreviousMonth);
			
			// Query to get orders with their visit data for the previous month
			String sql = "SELECT o.order_time_date, v.startTime, v.endTime, o.status, o.confirmation_code, v.is_reserved, v.is_late, v.billID " +
			             "FROM orders o " +
			             "LEFT JOIN visit v ON o.confirmation_code = v.confirmation_code " +
			             "WHERE o.order_time_date >= ? AND o.order_time_date < ? " +
			             "ORDER BY o.order_time_date";
			
			int totalClients = 0;
			int onTimeCount = 0;
			int late1to14Count = 0;
			int late15PlusCount = 0;
			int cancelledCount = 0;
			int mealUnder2HrCount = 0;
			int mealOver2HrCount = 0;
			int clientsWithMealDuration = 0;
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setTimestamp(1, startOfPreviousMonth);
				pstmt.setTimestamp(2, endOfPreviousMonth);
				
				System.out.println("🔍 Executing query with parameters:");
				System.out.println("   startOfPreviousMonth: " + startOfPreviousMonth);
				System.out.println("   endOfPreviousMonth: " + endOfPreviousMonth);
				
				try (ResultSet rs = pstmt.executeQuery()) {
					int rowCount = 0;
					while (rs.next()) {
						rowCount++;
						totalClients++;
						
						java.sql.Timestamp orderTime = rs.getTimestamp("order_time_date");
						java.sql.Timestamp startTime = rs.getTimestamp("startTime");
						java.sql.Timestamp endTime = rs.getTimestamp("endTime");
						String status = rs.getString("status");
						
						// Check if cancelled by restaurant
						boolean isCancelled = "cancelled by restaurant".equalsIgnoreCase(status) || 
						                      "Cancelled by restaurant".equalsIgnoreCase(status);
						
						// Calculate arrival delay
						if (orderTime != null && startTime != null) {
							long delayMinutes = java.time.Duration.between(
								orderTime.toInstant(), 
								startTime.toInstant()
							).toMinutes();
							
							if (delayMinutes == 0) {
								onTimeCount++;
							} else if (delayMinutes >= 1 && delayMinutes <= 14) {
								late1to14Count++;
							} else if (delayMinutes > 14) {
								late15PlusCount++;
								if (isCancelled) {
									cancelledCount++;
								}
							}
						} else if (isCancelled) {
							// If no startTime but cancelled, count as late >15 mins
							late15PlusCount++;
							cancelledCount++;
						}
						
						// Calculate meal duration
						if (startTime != null && endTime != null) {
							clientsWithMealDuration++;
							long durationHours = java.time.Duration.between(
								startTime.toInstant(), 
								endTime.toInstant()
							).toHours();
							
							if (durationHours < 2) {
								mealUnder2HrCount++;
							} else {
								mealOver2HrCount++;
							}
						}
					}
					System.out.println("📊 Found " + rowCount + " orders in the previous month");
				}
			}
			
			// Calculate percentages
			double onTimePercent = totalClients > 0 ? (onTimeCount * 100.0 / totalClients) : 0.0;
			double late1to14Percent = totalClients > 0 ? (late1to14Count * 100.0 / totalClients) : 0.0;
			double late15PlusPercent = totalClients > 0 ? (late15PlusCount * 100.0 / totalClients) : 0.0;
			double cancelledPercent = totalClients > 0 ? (cancelledCount * 100.0 / totalClients) : 0.0;
			double mealUnder2HrPercent = clientsWithMealDuration > 0 ? (mealUnder2HrCount * 100.0 / clientsWithMealDuration) : 0.0;
			double mealOver2HrPercent = clientsWithMealDuration > 0 ? (mealOver2HrCount * 100.0 / clientsWithMealDuration) : 0.0;
			
			// Format: "reportPeriod|onTimeCount|onTimePercent|late1to14Count|late1to14Percent|late15PlusCount|late15PlusPercent|cancelledCount|cancelledPercent|mealUnder2HrCount|mealUnder2HrPercent|mealOver2HrCount|mealOver2HrPercent|totalClients"
			String reportString = String.format("%s|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d",
				reportPeriod,
				onTimeCount, onTimePercent,
				late1to14Count, late1to14Percent,
				late15PlusCount, late15PlusPercent,
				cancelledCount, cancelledPercent,
				mealUnder2HrCount, mealUnder2HrPercent,
				mealOver2HrCount, mealOver2HrPercent,
				totalClients
			);
			
			System.out.println("📊 Report Statistics:");
			System.out.println("   Total Clients: " + totalClients);
			System.out.println("   On Time: " + onTimeCount + " (" + String.format("%.1f", onTimePercent) + "%)");
			System.out.println("   Late 1-14 mins: " + late1to14Count + " (" + String.format("%.1f", late1to14Percent) + "%)");
			System.out.println("   Late >15 mins: " + late15PlusCount + " (" + String.format("%.1f", late15PlusPercent) + "%)");
			System.out.println("   Cancelled: " + cancelledCount + " (" + String.format("%.1f", cancelledPercent) + "%)");
			System.out.println("   Meal <2hrs: " + mealUnder2HrCount + " (" + String.format("%.1f", mealUnder2HrPercent) + "%)");
			System.out.println("   Meal >2hrs: " + mealOver2HrCount + " (" + String.format("%.1f", mealOver2HrPercent) + "%)");
			
			reportData.add(reportString);
			System.out.println("✅ Report string added to list. List size: " + reportData.size());
			System.out.println("✅ Report string: " + reportString);
			
		} catch (SQLException e) {
			System.err.println("❌ Error getting delay chart report");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			// Return empty report with error indicator
			String errorReport = "ERROR|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(errorReport);
		} catch (Exception e) {
			System.err.println("❌ Unexpected error in GetDelayChartReport: " + e.getMessage());
			e.printStackTrace();
			// Return empty report with error indicator
			String errorReport = "ERROR|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(errorReport);
		}
		
		System.out.println("📤 Returning report data. List size: " + reportData.size());
		return reportData;
	}
	
	/**
	 * Get reservation chart report for custom months.
	 * @param customMonths Comma-separated list of months in "YYYY-MM" format (e.g., "2025-01,2025-02,2024-07")
	 * @return List with single string containing all statistics separated by |
	 * Format: "reportPeriod|reservationsCount|reservationsPercent|waitingListOrdersCount|waitingListOrdersPercent|totalWaitingList|checkedInCount|checkedInPercent|leftFromWaitingCount|leftFromWaitingPercent|totalOrders|totalWaitingListOutcomes"
	 */
	public static List<String> GetReservationChartReport(String customMonths) {
		List<String> reportData = new ArrayList<>();
		
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			String emptyReport = "NO_DATA|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0";
			reportData.add(emptyReport);
			return reportData;
		}
		
		try {
			List<java.time.YearMonth> months = new ArrayList<>();
			
			if (customMonths != null && !customMonths.isEmpty()) {
				// Parse custom months
				String[] monthStrings = customMonths.split(",");
				for (String monthStr : monthStrings) {
					try {
						java.time.YearMonth ym = java.time.YearMonth.parse(monthStr.trim());
						months.add(ym);
					} catch (Exception e) {
						System.err.println("❌ Invalid month format: " + monthStr);
					}
				}
			}
			
			if (months.isEmpty()) {
				// Default: previous month
				java.time.LocalDate now = java.time.LocalDate.now();
				java.time.LocalDate firstDayOfCurrentMonth = now.withDayOfMonth(1);
				java.time.LocalDate lastDayOfPreviousMonth = firstDayOfCurrentMonth.minusDays(1);
				java.time.LocalDate firstDayOfPreviousMonth = lastDayOfPreviousMonth.withDayOfMonth(1);
				months.add(java.time.YearMonth.from(firstDayOfPreviousMonth));
			}
			
			// sorting the months
			Collections.sort(months);
			String monthsJson = monthsToJson(months);
			
			// Try to use cached report
			String cached = findSavedReport("reservation", monthsJson);
			if (cached != null) {
				System.out.println("💾 Using cached reservation report for " + monthsJson);
				reportData.add(cached);
				return reportData;
			}
			
			// Build report period string
			StringBuilder reportPeriod = new StringBuilder();
			for (int i = 0; i < months.size(); i++) {
				if (i > 0) reportPeriod.append(" + ");
				reportPeriod.append(months.get(i).getMonth().toString()).append(" ").append(months.get(i).getYear());
			}
			
			System.out.println("📊 Generating Reservation Chart Report for: " + reportPeriod.toString());
			
			// Build WHERE clause for multiple months
			StringBuilder whereClauseOrders = new StringBuilder("(");
			StringBuilder whereClauseWaiting = new StringBuilder("(");
			for (int i = 0; i < months.size(); i++) {
				if (i > 0) {
					whereClauseOrders.append(" OR ");
					whereClauseWaiting.append(" OR ");
				}
				whereClauseOrders.append("(o.order_time_date >= ? AND o.order_time_date < ?)");
				whereClauseWaiting.append("(w.created_at >= ? AND w.created_at < ?)");
			}
			whereClauseOrders.append(")");
			whereClauseWaiting.append(")");
			
			// Count reservations from orders table
			String sqlOrders = "SELECT COUNT(*) as count FROM orders o WHERE " + whereClauseOrders.toString();
			
			// Count waiting list entries by status
			// Exclude "WAITING" status as it's just a default value that should be changed eventually
			// Past month reports should only show resolved entries (checkedin or left)
			String sqlWaiting = "SELECT status, COUNT(*) as count FROM waitingentry w WHERE " + whereClauseWaiting.toString() + " AND status != 'WAITING' AND status != 'waiting' GROUP BY status";
			
			int reservationsCount = 0;
			int waitingListOrdersCount = 0; // status = "checkedin"
			int totalWaitingList = 0;
			int checkedInCount = 0; // status = "checkedin"
			int leftFromWaitingCount = 0; // status = "left"
			
			// Count reservations
			try (PreparedStatement pstmt = conn.prepareStatement(sqlOrders)) {
				int paramIndex = 1;
				for (java.time.YearMonth ym : months) {
					java.sql.Timestamp monthStart = java.sql.Timestamp.valueOf(ym.atDay(1).atStartOfDay());
					java.sql.Timestamp monthEnd = java.sql.Timestamp.valueOf(ym.plusMonths(1).atDay(1).atStartOfDay());
					pstmt.setTimestamp(paramIndex++, monthStart);
					pstmt.setTimestamp(paramIndex++, monthEnd);
				}
				
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						reservationsCount = rs.getInt("count");
					}
				}
			}
			
			// Count waiting list entries
			try (PreparedStatement pstmt = conn.prepareStatement(sqlWaiting)) {
				int paramIndex = 1;
				for (java.time.YearMonth ym : months) {
					java.sql.Timestamp monthStart = java.sql.Timestamp.valueOf(ym.atDay(1).atStartOfDay());
					java.sql.Timestamp monthEnd = java.sql.Timestamp.valueOf(ym.plusMonths(1).atDay(1).atStartOfDay());
					pstmt.setTimestamp(paramIndex++, monthStart);
					pstmt.setTimestamp(paramIndex++, monthEnd);
				}
				
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						String status = rs.getString("status");
						int count = rs.getInt("count");
						
						if (status != null) {
							String statusLower = status.toLowerCase();
							if (statusLower.equals("checkedin")) {
								waitingListOrdersCount = count;
								checkedInCount = count;
								totalWaitingList += count;
							} else if (statusLower.equals("left")) {
								leftFromWaitingCount = count;
								totalWaitingList += count;
							}
							// Note: WAITING status is excluded from the query (it's just a default value)
							// Past month reports should only show resolved entries (checkedin or left)
						}
					}
				}
			}
			
			// Calculate totals
			// Total orders = reservations + waiting list orders (checkedin) - NOT including left
			int totalOrders = reservationsCount + waitingListOrdersCount;
			
			// Calculate percentages for first chart (Total Orders Made)
			double reservationsPercent = totalOrders > 0 ? (reservationsCount * 100.0 / totalOrders) : 0.0;
			double waitingListOrdersPercent = totalOrders > 0 ? (waitingListOrdersCount * 100.0 / totalOrders) : 0.0;
			
			// Calculate percentages for second chart (Waiting List Outcomes)
			// Total waiting list entries that had an outcome (checkedin or left)
			int totalWaitingListOutcomes = checkedInCount + leftFromWaitingCount;
			double checkedInPercent = totalWaitingListOutcomes > 0 ? (checkedInCount * 100.0 / totalWaitingListOutcomes) : 0.0;
			double leftFromWaitingPercent = totalWaitingListOutcomes > 0 ? (leftFromWaitingCount * 100.0 / totalWaitingListOutcomes) : 0.0;
			
			// Format: "reportPeriod|reservationsCount|reservationsPercent|waitingListOrdersCount|waitingListOrdersPercent|totalWaitingList|checkedInCount|checkedInPercent|leftFromWaitingCount|leftFromWaitingPercent|totalOrders|totalWaitingListOutcomes"
			String reportString = String.format("%s|%d|%.2f|%d|%.2f|%d|%d|%.2f|%d|%.2f|%d|%d",
				reportPeriod.toString(),
				reservationsCount, reservationsPercent,
				waitingListOrdersCount, waitingListOrdersPercent,
				totalWaitingList,
				checkedInCount, checkedInPercent,
				leftFromWaitingCount, leftFromWaitingPercent,
				totalOrders,
				totalWaitingListOutcomes
			);
			
			System.out.println("📊 Report Statistics:");
			System.out.println("   Total Orders Made: " + totalOrders);
			System.out.println("   From Reservations: " + reservationsCount + " (" + String.format("%.1f", reservationsPercent) + "%)");
			System.out.println("   From Waiting List: " + waitingListOrdersCount + " (" + String.format("%.1f", waitingListOrdersPercent) + "%)");
			System.out.println("   Total Waiting List Entries: " + totalWaitingList);
			System.out.println("   Waiting List Outcomes: " + totalWaitingListOutcomes);
			System.out.println("   Checked In: " + checkedInCount + " (" + String.format("%.1f", checkedInPercent) + "%)");
			System.out.println("   Left From Waiting: " + leftFromWaitingCount + " (" + String.format("%.1f", leftFromWaitingPercent) + "%)");
			
			// saving the report for future retrieval
			System.out.println("💾 Preparing to save reservation report...");
			try {
				java.sql.Timestamp createdAt = firstMonthStart(months);
				System.out.println("💾 Created timestamp: " + createdAt);
				System.out.println("💾 Months JSON: " + monthsToJson(months));
				saveReport("reservation", monthsToJson(months), reportPeriod.toString(), reportString, createdAt);
			} catch (Exception e) {
				System.err.println("⚠️ Failed to save reservation report: " + e.getMessage());
				e.printStackTrace();
			}
			
			reportData.add(reportString);
			System.out.println("✅ Report string added to list. List size: " + reportData.size());
			
		} catch (SQLException e) {
			System.err.println("❌ Error getting reservation chart report");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
			String errorReport = "ERROR|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0";
			reportData.add(errorReport);
		} catch (Exception e) {
			System.err.println("❌ Unexpected error in GetReservationChartReport: " + e.getMessage());
			e.printStackTrace();
			String errorReport = "ERROR|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0";
			reportData.add(errorReport);
		}
		
		System.out.println("📤 Returning report data. List size: " + reportData.size());
		return reportData;
	}
	
	/**
	 * Get reservation chart report for the previous month (default).
	 * @return List with single string containing all statistics separated by |
	 */
	public static List<String> GetReservationChartReport() {
		return GetReservationChartReport(null);
	}

}
