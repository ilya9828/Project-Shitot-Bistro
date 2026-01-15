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
import entities.WaitingEntry;
import entities.Payment;

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
			startWaitingListProcessingThread();
			startReminderNotificationThread();
			startExpiredReservationCancellationThread();
			startLongSittingCheckThread();
					
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
						// First, deactivate ALL existing permanent entries for this day (to handle multiple permanent entries)
						String deactivatePermanentSql = "UPDATE opening_hours SET is_active = false WHERE day_of_week = ? AND is_permanent = 1 AND specific_date IS NULL";
						try (PreparedStatement deactivatePermanentStmt = conn.prepareStatement(deactivatePermanentSql)) {
							deactivatePermanentStmt.setString(1, day);
							deactivatePermanentStmt.executeUpdate();
						}
						
						// Also deactivate all non-permanent entries (specific date entries) for this day
						String deactivateNonPermanentSql = "UPDATE opening_hours SET is_active = false WHERE day_of_week = ? AND (is_permanent != 1 OR specific_date IS NOT NULL)";
						try (PreparedStatement deactivateNonPermanentStmt = conn.prepareStatement(deactivateNonPermanentSql)) {
							deactivateNonPermanentStmt.setString(1, day);
							deactivateNonPermanentStmt.executeUpdate();
						}
						
						// Now insert or update the permanent entry (this will be the only active permanent entry for this day)
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
			String sql = "SELECT t.tableID, t.capacity, o.name AS customerName, v.startTime AS checkInTime, t.confirmationCode " +
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
			System.err.println("Database connection is null!");
			return waitingList;
		}

		try {
			String sql = "SELECT waitingID, number_of_guests, phone, date, status, created_at FROM waitingentry WHERE created_at >= NOW() AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY) ORDER BY waitingID";
			
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
	 * Get confirmation codes for a subscriber's reservations today
	 * @param subscriberID - the subscriber ID
	 * @return List of confirmation codes as strings for today's reservations
	 */
	public static List<String> GetSubscriberTodayConfirmationCodes(String subscriberID) {
		List<String> confirmationCodes = new ArrayList<>();
		
		if (conn == null) {
			System.err.println("❌ Database connection is null!");
			return confirmationCodes;
		}

		try {
			// Get confirmation codes for today's reservations where subscriber_id matches
			String sql = "SELECT confirmation_code, order_time_date " +
			             "FROM orders " +
			             "WHERE subscriber_id = ? " +
			             "AND DATE(order_time_date) = CURDATE() " +
			             "AND status NOT IN ('cancelled', 'Cancelled by user', 'Cancelled by resturant', 'paid', 'CheckedIN') " +
			             "ORDER BY order_time_date ASC";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				try {
					int subscriberIdInt = Integer.parseInt(subscriberID);
					pstmt.setInt(1, subscriberIdInt);
				} catch (NumberFormatException e) {
					System.err.println("❌ Invalid subscriber ID format: " + subscriberID);
					return confirmationCodes;
				}
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						String confirmation_code = rs.getString("confirmation_code");
						if (confirmation_code != null && !confirmation_code.trim().isEmpty()) {
							confirmationCodes.add(confirmation_code);
						}
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Error getting subscriber today confirmation codes");
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("Message: " + e.getMessage());
			e.printStackTrace();
		}
		
		return confirmationCodes;
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
			
			String sql = "SELECT o.order_time_date, v.startTime, v.endTime, o.status, o.confirmation_code " +
			             "FROM orders o " +
			             "LEFT JOIN visit v ON o.confirmation_code = v.confirmation_code " +
			             "WHERE " + whereClause.toString() + " " +
			             "ORDER BY o.order_time_date";
			
			int totalClients = 0;
			int onTimeCount = 0;
			int late1to14Count = 0;
			int late15PlusCount = 0;
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
						
						java.sql.Timestamp orderTime = rs.getTimestamp("order_time_date");
						java.sql.Timestamp startTime = rs.getTimestamp("startTime");
						java.sql.Timestamp endTime = rs.getTimestamp("endTime");
						String status = rs.getString("status");
						
						boolean isCancelled = "cancelled by restaurant".equalsIgnoreCase(status) || 
						                      "Cancelled by restaurant".equalsIgnoreCase(status) ||
						                      "Cancelled by resturant".equalsIgnoreCase(status);
						
						// Only count orders that were checked in (have startTime) or were cancelled
						if (orderTime != null && startTime != null) {
							totalClients++;
							long delayMinutes = java.time.Duration.between(
								orderTime.toInstant(), 
								startTime.toInstant()
							).toMinutes();
							
							if (delayMinutes == 0) {
								onTimeCount++;
							} else if (delayMinutes >= 1 && delayMinutes < 15) {
								late1to14Count++;
							} else if (delayMinutes >= 15) {
								late15PlusCount++;
							}
						} else if (isCancelled) {
							totalClients++;
							late15PlusCount++;
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
			double mealUnder2HrPercent = clientsWithMealDuration > 0 ? (mealUnder2HrCount * 100.0 / clientsWithMealDuration) : 0.0;
			double mealOver2HrPercent = clientsWithMealDuration > 0 ? (mealOver2HrCount * 100.0 / clientsWithMealDuration) : 0.0;
			
			// Format: "reportPeriod|onTimeCount|onTimePercent|late1to14Count|late1to14Percent|late15PlusCount|late15PlusPercent|mealUnder2HrCount|mealUnder2HrPercent|mealOver2HrCount|mealOver2HrPercent|totalClients"
			String reportString = String.format("%s|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d",
				reportPeriod.toString(),
				onTimeCount, onTimePercent,
				late1to14Count, late1to14Percent,
				late15PlusCount, late15PlusPercent,
				mealUnder2HrCount, mealUnder2HrPercent,
				mealOver2HrCount, mealOver2HrPercent,
				totalClients
			);
			
			System.out.println("📊 Report Statistics:");
			System.out.println("   Total Clients: " + totalClients);
			System.out.println("   On Time: " + onTimeCount + " (" + String.format("%.1f", onTimePercent) + "%)");
			System.out.println("   Late 1-14 mins: " + late1to14Count + " (" + String.format("%.1f", late1to14Percent) + "%)");
			System.out.println("   Late >15 mins: " + late15PlusCount + " (" + String.format("%.1f", late15PlusPercent) + "%)");
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
	 * Format: "reportPeriod|onTimeCount|onTimePercent|late1to14Count|late1to14Percent|late15PlusCount|late15PlusPercent|mealUnder2HrCount|mealUnder2HrPercent|mealOver2HrCount|mealOver2HrPercent|totalClients"
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
			String sql = "SELECT o.order_time_date, v.startTime, v.endTime, o.status, o.confirmation_code " +
			             "FROM orders o " +
			             "LEFT JOIN visit v ON o.confirmation_code = v.confirmation_code " +
			             "WHERE o.order_time_date >= ? AND o.order_time_date < ? " +
			             "ORDER BY o.order_time_date";
			
			int totalClients = 0;
			int onTimeCount = 0;
			int late1to14Count = 0;
			int late15PlusCount = 0;
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
						
						java.sql.Timestamp orderTime = rs.getTimestamp("order_time_date");
						java.sql.Timestamp startTime = rs.getTimestamp("startTime");
						java.sql.Timestamp endTime = rs.getTimestamp("endTime");
						String status = rs.getString("status");
						
						// Check if cancelled by restaurant
						boolean isCancelled = "Cancelled by resturant".equalsIgnoreCase(status);
						
						// Only count orders that were checked in (have startTime) or were cancelled
						// Calculate arrival delay
						if (orderTime != null && startTime != null) {
							totalClients++;
							long delayMinutes = java.time.Duration.between(
								orderTime.toInstant(), 
								startTime.toInstant()
							).toMinutes();
							
							if (delayMinutes == 0) {
								onTimeCount++;
							} else if (delayMinutes >= 1 && delayMinutes < 15) {
								late1to14Count++;
							} else if (delayMinutes >= 15) {
								late15PlusCount++;
							}
						} else if (isCancelled) {
							// If no startTime but cancelled, count as late >15 mins
							totalClients++;
							late15PlusCount++;
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
			double mealUnder2HrPercent = clientsWithMealDuration > 0 ? (mealUnder2HrCount * 100.0 / clientsWithMealDuration) : 0.0;
			double mealOver2HrPercent = clientsWithMealDuration > 0 ? (mealOver2HrCount * 100.0 / clientsWithMealDuration) : 0.0;
			
			// Format: "reportPeriod|onTimeCount|onTimePercent|late1to14Count|late1to14Percent|late15PlusCount|late15PlusPercent|mealUnder2HrCount|mealUnder2HrPercent|mealOver2HrCount|mealOver2HrPercent|totalClients"
			String reportString = String.format("%s|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d|%.2f|%d",
				reportPeriod,
				onTimeCount, onTimePercent,
				late1to14Count, late1to14Percent,
				late15PlusCount, late15PlusPercent,
				mealUnder2HrCount, mealUnder2HrPercent,
				mealOver2HrCount, mealOver2HrPercent,
				totalClients
			);
			
			System.out.println("📊 Report Statistics:");
			System.out.println("   Total Clients: " + totalClients);
			System.out.println("   On Time: " + onTimeCount + " (" + String.format("%.1f", onTimePercent) + "%)");
			System.out.println("   Late 1-14 mins: " + late1to14Count + " (" + String.format("%.1f", late1to14Percent) + "%)");
			System.out.println("   Late >15 mins: " + late15PlusCount + " (" + String.format("%.1f", late15PlusPercent) + "%)");
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
	 * Format: "reportPeriod|successfulVisitsCount|successfulVisitsPercent|unsuccessfulVisitsCount|unsuccessfulVisitsPercent|totalWaitingList|checkedInCount|checkedInPercent|leftFromWaitingCount|leftFromWaitingPercent|totalVisits|totalWaitingListOutcomes"
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
			
			// Count orders by status (paid vs not paid) for Total Visits chart
			String sqlOrders = "SELECT status, COUNT(*) as count FROM orders o WHERE " + whereClauseOrders.toString() + " GROUP BY status";
			
			// Count waiting list entries by status
			// Exclude "WAITING" status as it's just a default value that should be changed eventually
			// Past month reports should only show resolved entries (seated or left)
			String sqlWaiting = "SELECT status, COUNT(*) as count FROM waitingentry w WHERE " + whereClauseWaiting.toString() + " AND status != 'WAITING' AND status != 'waiting' GROUP BY status";
			
			int successfulVisitsCount = 0; // status = "paid"
			int unsuccessfulVisitsCount = 0; // status != "paid"
			int totalWaitingList = 0;
			int checkedInCount = 0; // status = "SEATED" 
			int leftFromWaitingCount = 0; // status = "left"
			
			// Count orders by status (paid vs not paid)
			try (PreparedStatement pstmt = conn.prepareStatement(sqlOrders)) {
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
						
						if (status != null && status.equalsIgnoreCase("paid")) {
							successfulVisitsCount += count;
						} else {
							unsuccessfulVisitsCount += count;
						}
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
							if (statusLower.equals("seated")) {
								checkedInCount = count;
								totalWaitingList += count;
							} else if (statusLower.equals("left")) {
								leftFromWaitingCount = count;
								totalWaitingList += count;
							}
							// Note: WAITING status is excluded from the query (it's just a default value)
							// Past month reports should only show resolved entries (seated or left)
						}
					}
				}
			}
			
			// Calculate totals
			// Total visits = all orders (successful + unsuccessful)
			int totalVisits = successfulVisitsCount + unsuccessfulVisitsCount;
			
			// Calculate percentages for first chart (Total Visits)
			double successfulVisitsPercent = totalVisits > 0 ? (successfulVisitsCount * 100.0 / totalVisits) : 0.0;
			double unsuccessfulVisitsPercent = totalVisits > 0 ? (unsuccessfulVisitsCount * 100.0 / totalVisits) : 0.0;
			
			// Calculate percentages for second chart (Waiting List Outcomes)
			// Total waiting list entries that had an outcome (checkedin or left)
			int totalWaitingListOutcomes = checkedInCount + leftFromWaitingCount;
			double checkedInPercent = totalWaitingListOutcomes > 0 ? (checkedInCount * 100.0 / totalWaitingListOutcomes) : 0.0;
			double leftFromWaitingPercent = totalWaitingListOutcomes > 0 ? (leftFromWaitingCount * 100.0 / totalWaitingListOutcomes) : 0.0;
			
			// Format: "reportPeriod|successfulVisitsCount|successfulVisitsPercent|unsuccessfulVisitsCount|unsuccessfulVisitsPercent|totalWaitingList|checkedInCount|checkedInPercent|leftFromWaitingCount|leftFromWaitingPercent|totalVisits|totalWaitingListOutcomes"
			String reportString = String.format("%s|%d|%.2f|%d|%.2f|%d|%d|%.2f|%d|%.2f|%d|%d",
				reportPeriod.toString(),
				successfulVisitsCount, successfulVisitsPercent,
				unsuccessfulVisitsCount, unsuccessfulVisitsPercent,
				totalWaitingList,
				checkedInCount, checkedInPercent,
				leftFromWaitingCount, leftFromWaitingPercent,
				totalVisits,
				totalWaitingListOutcomes
			);
			
			System.out.println("📊 Report Statistics:");
			System.out.println("   Total Visits: " + totalVisits);
			System.out.println("   Successful Visits: " + successfulVisitsCount + " (" + String.format("%.1f", successfulVisitsPercent) + "%)");
			System.out.println("   Unsuccessful Visits: " + unsuccessfulVisitsCount + " (" + String.format("%.1f", unsuccessfulVisitsPercent) + "%)");
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
	 * Cancel reservations that are more than 15 minutes past their reservation time
	 * and have not been checked in (no visit record exists).
	 * This method is called periodically by a background thread.
	 */
	private static void cancelExpiredReservations() {
		try {
			String cancelSql = 
				"UPDATE orders o " +
				"SET o.status = 'Cancelled by resturant' " +
				"WHERE o.order_time_date <= DATE_SUB(NOW(), INTERVAL 15 MINUTE) " +
				"AND o.status NOT IN ('cancelled', 'Cancelled by user', 'Cancelled by resturant', 'paid', 'CheckedIN')";
			
			try (PreparedStatement cancelStmt = conn.prepareStatement(cancelSql)) {
				cancelStmt.executeUpdate();
			}
		} catch (SQLException e) {
			System.err.println("[Cancellation Thread] Error cancelling expired reservations: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("[Cancellation Thread] Unexpected error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Start a background thread that periodically checks and cancels expired reservations.
	 * The thread runs every 30 seconds to check for reservations that are more than 15 minutes
	 * past their reservation time and have not been checked in.
	 */
	private static void startExpiredReservationCancellationThread() {
		Thread cancellationThread = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(30000);
					if (conn != null && !conn.isClosed()) {
						cancelExpiredReservations();
					}
				} catch (InterruptedException e) {
					break;
				} catch (SQLException e) {
					System.err.println("[Cancellation Thread] Database connection error: " + e.getMessage());
					e.printStackTrace();
				} catch (Exception e) {
					System.err.println("[Cancellation Thread] Unexpected error: " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
		
		cancellationThread.setDaemon(true);
		cancellationThread.setName("ExpiredReservationCancellation");
		cancellationThread.start();
	}
	
	/**
	 * Process waiting list entries - check if there's available capacity and automatically check-in customers.
	 * This method is called periodically by a background thread.
	 * Priority: P_WAITING (high priority) first, then WAITING (low priority).
	 */
	private static void processWaitingList() {
		try {
			Timestamp currentTime = new Timestamp(System.currentTimeMillis());
			
			// First, get P_WAITING entries (high priority)
			String getPWaitingListSql = 
				"SELECT confirmation_code, number_of_guests, phone, email, status " +
				"FROM waitingentry " +
				"WHERE status = 'P_WAITING' " +
				"ORDER BY created_at ASC";
			
			List<WaitingListEntry> pWaitingEntries = new ArrayList<>();
			try (PreparedStatement stmt = conn.prepareStatement(getPWaitingListSql)) {
				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						WaitingListEntry entry = new WaitingListEntry();
						entry.confirmationCode = rs.getString("confirmation_code");
						entry.numberOfGuests = rs.getInt("number_of_guests");
						entry.phone = rs.getString("phone");
						entry.email = rs.getString("email");
						entry.status = rs.getString("status");
						pWaitingEntries.add(entry);
					}
				}
			}
			
			// Process P_WAITING entries first
			boolean pWaitingProcessed = false;
			for (WaitingListEntry entry : pWaitingEntries) {
				System.out.println("[DEBUG processWaitingList] Processing P_WAITING entry: confirmation_code=" + entry.confirmationCode + ", numberOfGuests=" + entry.numberOfGuests);
				// Check if there's already a PENDING order for this confirmation_code (to avoid double-counting)
				boolean hasPendingOrder = false;
				try (PreparedStatement checkPendingStmt = conn.prepareStatement(
						"SELECT COUNT(*) FROM orders WHERE confirmation_code = ? AND status = 'PENDING'")) {
					checkPendingStmt.setString(1, entry.confirmationCode);
					try (ResultSet rs = checkPendingStmt.executeQuery()) {
						if (rs.next() && rs.getInt(1) > 0) {
							hasPendingOrder = true;
						}
					}
				} catch (SQLException e) {
					System.err.println("[Waiting List Processor] Error checking for PENDING order: " + e.getMessage());
				}
				
				boolean hasCapacity;
				if (hasPendingOrder) {
					// Exclude the existing PENDING order to avoid double-counting
					hasCapacity = checkCapacityAtTimeExcludingOrderByGuests(currentTime, entry.confirmationCode, entry.numberOfGuests);
				} else {
					hasCapacity = checkCapacityAtTimeByGuests(currentTime, entry.numberOfGuests);
				}
				
				if (hasCapacity) {
					// Use an atomic UPDATE to reserve a table (only succeeds if table is still AVAILABLE)
					// This prevents concurrent assignments of the same table
					// Set both confirmationCode and tableStatus to 'OCCUPIED' so the 2nd query won't find any AVAILABLE tables
					String reserveTableSql =
						"UPDATE tables SET confirmationCode = ?, tableStatus = 'OCCUPIED' " +
						"WHERE tableID = (" +
						"  SELECT tableID FROM (" +
						"    SELECT tableID FROM tables " +
						"    WHERE tableStatus = 'AVAILABLE' AND capacity >= ? " +
						"    ORDER BY capacity ASC " +
						"    LIMIT 1" +
						"  ) AS temp" +
						") AND tableStatus = 'AVAILABLE'";
					
					Integer tableId = null;
					int rowsUpdated = 0;
					
					try (PreparedStatement reserveStmt = conn.prepareStatement(reserveTableSql)) {
						reserveStmt.setString(1, entry.confirmationCode);
						reserveStmt.setInt(2, entry.numberOfGuests);
						rowsUpdated = reserveStmt.executeUpdate();
					}
					
					// If we successfully reserved a table, get its ID
					if (rowsUpdated > 0) {
						String getTableIdSql = "SELECT tableID FROM tables WHERE confirmationCode = ? LIMIT 1";
						try (PreparedStatement getTableIdStmt = conn.prepareStatement(getTableIdSql)) {
							getTableIdStmt.setString(1, entry.confirmationCode);
							try (ResultSet rs = getTableIdStmt.executeQuery()) {
								if (rs.next()) {
									tableId = rs.getInt("tableID");
								}
							}
						}
					}
					
					if (tableId != null) {
						// Table is available - send SMS and create PENDING order
						try {
							Integer subscriberId = null;
							String name = null;
							boolean isSubscriber = false;
							
							if (entry.phone != null || entry.email != null) {
								String subCheckSql = "SELECT subscriberID, name FROM subscriber WHERE phone = ? OR email = ?";
								try (PreparedStatement subCheckStmt = conn.prepareStatement(subCheckSql)) {
									subCheckStmt.setString(1, entry.phone);
									subCheckStmt.setString(2, entry.email);
									try (ResultSet subRs = subCheckStmt.executeQuery()) {
										if (subRs.next()) {
											subscriberId = subRs.getInt("subscriberID");
											name = subRs.getString("name");
											isSubscriber = true;
										}
									}
								}
							}
							
							String customerName = name != null ? name : (entry.email != null ? entry.email : entry.phone);
							System.out.println("[Waiting List Processor] SMS sent to customer: " + customerName + 
							                   " - Table " + tableId + " is ready!");
							
							// Update waitingentry status to SEATED when SMS is sent
							try {
								String updateWaitingSql = "UPDATE waitingentry SET status = 'SEATED' WHERE confirmation_code = ?";
								try (PreparedStatement updateWaiting = conn.prepareStatement(updateWaitingSql)) {
									updateWaiting.setString(1, entry.confirmationCode);
									updateWaiting.executeUpdate();
									System.out.println("[Waiting List Processor] Updated waitingentry status to SEATED for confirmation_code: " + entry.confirmationCode);
								}
							} catch (SQLException e) {
								System.err.println("[Waiting List Processor] Error updating waitingentry status: " + e.getMessage());
								e.printStackTrace();
							}
							
							// Create a new order with status PENDING when SMS is sent (table is available)
							try {
								String newOrderSql =
									"INSERT INTO orders " +
									"(subscriber_id, number_of_guests, confirmation_code, order_number, " +
									"order_time_date, time_date_of_placing_order, status, is_subscriber, email, phone, name) " +
									"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
								
								try (PreparedStatement newOrderStmt = conn.prepareStatement(newOrderSql)) {
									newOrderStmt.setObject(1, subscriberId, java.sql.Types.INTEGER);
									newOrderStmt.setInt(2, entry.numberOfGuests);
									newOrderStmt.setString(3, entry.confirmationCode);
									newOrderStmt.setString(4, entry.confirmationCode);
									newOrderStmt.setTimestamp(5, currentTime);
									newOrderStmt.setTimestamp(6, currentTime);
									newOrderStmt.setString(7, "PENDING");
									newOrderStmt.setBoolean(8, isSubscriber);
									newOrderStmt.setString(9, entry.email);
									newOrderStmt.setString(10, entry.phone);
									newOrderStmt.setString(11, name);
									newOrderStmt.executeUpdate();
									System.out.println("[Waiting List Processor] Created new PENDING order for confirmation_code: " + entry.confirmationCode);
								}
							} catch (SQLException e) {
								System.err.println("[Waiting List Processor] Error creating PENDING order: " + e.getMessage());
								e.printStackTrace();
							}
							
							pWaitingProcessed = true;
							// Add a small delay to prevent concurrent processing
							try {
								Thread.sleep(2000); // 2 seconds delay
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}
							break; // Process only one entry per cycle
						} catch (Exception e) {
							System.err.println("[Waiting List Processor] Error processing waiting list entry: " + e.getMessage());
							e.printStackTrace();
						}
					}
				}
			}
			
			// Only process WAITING entries if P_WAITING list was empty or no entry was processed
			if (!pWaitingProcessed) {
				// Get WAITING entries (low priority)
				String getWaitingListSql = 
					"SELECT confirmation_code, number_of_guests, phone, email, status " +
					"FROM waitingentry " +
					"WHERE status = 'WAITING' " +
					"ORDER BY created_at ASC";
				
				List<WaitingListEntry> waitingEntries = new ArrayList<>();
				try (PreparedStatement stmt = conn.prepareStatement(getWaitingListSql)) {
					try (ResultSet rs = stmt.executeQuery()) {
						while (rs.next()) {
							WaitingListEntry entry = new WaitingListEntry();
							entry.confirmationCode = rs.getString("confirmation_code");
							entry.numberOfGuests = rs.getInt("number_of_guests");
							entry.phone = rs.getString("phone");
							entry.email = rs.getString("email");
							entry.status = rs.getString("status");
							waitingEntries.add(entry);
						}
					}
				}
				
				// Process WAITING entries
				for (WaitingListEntry entry : waitingEntries) {
					System.out.println("[DEBUG processWaitingList] Processing WAITING entry: confirmation_code=" + entry.confirmationCode + ", numberOfGuests=" + entry.numberOfGuests);
					// Check if there's already a PENDING order for this confirmation_code (to avoid double-counting)
					boolean hasPendingOrder = false;
					try (PreparedStatement checkPendingStmt = conn.prepareStatement(
							"SELECT COUNT(*) FROM orders WHERE confirmation_code = ? AND status = 'PENDING'")) {
						checkPendingStmt.setString(1, entry.confirmationCode);
						try (ResultSet rs = checkPendingStmt.executeQuery()) {
							if (rs.next() && rs.getInt(1) > 0) {
								hasPendingOrder = true;
							}
						}
					} catch (SQLException e) {
						System.err.println("[Waiting List Processor] Error checking for PENDING order: " + e.getMessage());
					}
					
					boolean hasCapacity;
					if (hasPendingOrder) {
						// Exclude the existing PENDING order to avoid double-counting
						hasCapacity = checkCapacityAtTimeExcludingOrderByGuests(currentTime, entry.confirmationCode, entry.numberOfGuests);
					} else {
						hasCapacity = checkCapacityAtTimeByGuests(currentTime, entry.numberOfGuests);
					}
					
					if (hasCapacity) {
						// Use an atomic UPDATE to reserve a table (only succeeds if table is still AVAILABLE)
						// This prevents concurrent assignments of the same table
						String reserveTableSql =
							"UPDATE tables SET confirmationCode = ? " +
							"WHERE tableID = (" +
							"  SELECT tableID FROM (" +
							"    SELECT tableID FROM tables " +
							"    WHERE tableStatus = 'AVAILABLE' AND capacity >= ? " +
							"    ORDER BY capacity ASC " +
							"    LIMIT 1" +
							"  ) AS temp" +
							") AND tableStatus = 'AVAILABLE'";
						
						Integer tableId = null;
						int rowsUpdated = 0;
						
						try (PreparedStatement reserveStmt = conn.prepareStatement(reserveTableSql)) {
							reserveStmt.setString(1, entry.confirmationCode);
							reserveStmt.setInt(2, entry.numberOfGuests);
							rowsUpdated = reserveStmt.executeUpdate();
						}
						
						// If we successfully reserved a table, get its ID
						if (rowsUpdated > 0) {
							String getTableIdSql = "SELECT tableID FROM tables WHERE confirmationCode = ? LIMIT 1";
							try (PreparedStatement getTableIdStmt = conn.prepareStatement(getTableIdSql)) {
								getTableIdStmt.setString(1, entry.confirmationCode);
								try (ResultSet rs = getTableIdStmt.executeQuery()) {
									if (rs.next()) {
										tableId = rs.getInt("tableID");
									}
								}
							}
						}
						
						if (tableId != null) {
							// Table is available - send SMS and create PENDING order
							try {
								Integer subscriberId = null;
								String name = null;
								boolean isSubscriber = false;
								
								if (entry.phone != null || entry.email != null) {
									String subCheckSql = "SELECT subscriberID, name FROM subscriber WHERE phone = ? OR email = ?";
									try (PreparedStatement subCheckStmt = conn.prepareStatement(subCheckSql)) {
										subCheckStmt.setString(1, entry.phone);
										subCheckStmt.setString(2, entry.email);
										try (ResultSet subRs = subCheckStmt.executeQuery()) {
											if (subRs.next()) {
												subscriberId = subRs.getInt("subscriberID");
												name = subRs.getString("name");
												isSubscriber = true;
											}
										}
									}
								}
								
								String customerName = name != null ? name : (entry.email != null ? entry.email : entry.phone);
								System.out.println("[Waiting List Processor] SMS sent to customer: " + customerName + 
								                   " - Table " + tableId + " is ready!");
								
								// Update waitingentry status to SEATED when SMS is sent
								try {
									String updateWaitingSql = "UPDATE waitingentry SET status = 'SEATED' WHERE confirmation_code = ?";
									try (PreparedStatement updateWaiting = conn.prepareStatement(updateWaitingSql)) {
										updateWaiting.setString(1, entry.confirmationCode);
										updateWaiting.executeUpdate();
										System.out.println("[Waiting List Processor] Updated waitingentry status to SEATED for confirmation_code: " + entry.confirmationCode);
									}
								} catch (SQLException e) {
									System.err.println("[Waiting List Processor] Error updating waitingentry status: " + e.getMessage());
									e.printStackTrace();
								}
								
								// Create a new order with status PENDING when SMS is sent (table is available)
								try {
									String newOrderSql =
										"INSERT INTO orders " +
										"(subscriber_id, number_of_guests, confirmation_code, order_number, " +
										"order_time_date, time_date_of_placing_order, status, is_subscriber, email, phone, name) " +
										"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
									
									try (PreparedStatement newOrderStmt = conn.prepareStatement(newOrderSql)) {
										newOrderStmt.setObject(1, subscriberId, java.sql.Types.INTEGER);
										newOrderStmt.setInt(2, entry.numberOfGuests);
										newOrderStmt.setString(3, entry.confirmationCode);
										newOrderStmt.setString(4, entry.confirmationCode);
										newOrderStmt.setTimestamp(5, currentTime);
										newOrderStmt.setTimestamp(6, currentTime);
										newOrderStmt.setString(7, "PENDING");
										newOrderStmt.setBoolean(8, isSubscriber);
										newOrderStmt.setString(9, entry.email);
										newOrderStmt.setString(10, entry.phone);
										newOrderStmt.setString(11, name);
										newOrderStmt.executeUpdate();
										System.out.println("[Waiting List Processor] Created new PENDING order for confirmation_code: " + entry.confirmationCode);
									}
								} catch (SQLException e) {
									System.err.println("[Waiting List Processor] Error creating PENDING order: " + e.getMessage());
									e.printStackTrace();
								}
								
								// Add a small delay to prevent concurrent processing
								try {
									Thread.sleep(2000); // 2 seconds delay
								} catch (InterruptedException e) {
									Thread.currentThread().interrupt();
								}
								break; // Process only one entry per cycle
							} catch (Exception e) {
								System.err.println("[Waiting List Processor] Error processing waiting list entry: " + e.getMessage());
								e.printStackTrace();
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("[Waiting List Processor] Error processing waiting list: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("[Waiting List Processor] Unexpected error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Helper class to hold waiting list entry data
	 */
	private static class WaitingListEntry {
		String confirmationCode;
		int numberOfGuests;
		String phone;
		String email;
		String status;
	}
	
	/**
	 * Start a background thread that periodically processes the waiting list.
	 * The thread runs every 30 seconds to check if there's available capacity
	 * and automatically check-in customers from the waiting list.
	 */
	private static void startWaitingListProcessingThread() {
		Thread waitingListThread = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(3000);
					if (conn != null && !conn.isClosed()) {
						processWaitingList();
					}
				} catch (InterruptedException e) {
					break;
				} catch (SQLException e) {
					System.err.println("[Waiting List Processor] Database connection error: " + e.getMessage());
					e.printStackTrace();
				} catch (Exception e) {
					System.err.println("[Waiting List Processor] Unexpected error: " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
		
		waitingListThread.setDaemon(true);
		waitingListThread.setName("WaitingListProcessor");
		waitingListThread.start();
	}
	
	/**
	 * Send reminder notifications (SMS and Email) to customers 2 hours before their reservation time.
	 * This method is called periodically by a background thread.
	 */
	private static void sendReminderNotifications() {
		try {
			String reminderSql = 
				"SELECT confirmation_code, order_time_date, name, email, phone, number_of_guests, is_subscriber " +
				"FROM orders " +
				"WHERE order_time_date >= DATE_ADD(NOW(), INTERVAL 2 HOUR) " +
				"AND order_time_date <= DATE_ADD(DATE_ADD(NOW(), INTERVAL 2 HOUR), INTERVAL 5 MINUTE) " +
				"AND status NOT IN ('cancelled', 'Cancelled by user', 'Cancelled by resturant', 'paid', 'CheckedIN') " +
				"AND order_time_date > NOW()";
			
			try (PreparedStatement reminderStmt = conn.prepareStatement(reminderSql)) {
				try (ResultSet rs = reminderStmt.executeQuery()) {
					while (rs.next()) {
						String confirmationCode = rs.getString("confirmation_code");
						Timestamp orderTime = rs.getTimestamp("order_time_date");
						String name = rs.getString("name");
						String email = rs.getString("email");
						String phone = rs.getString("phone");
						int numberOfGuests = rs.getInt("number_of_guests");
						boolean isSubscriber = rs.getBoolean("is_subscriber");
						
						String customerName = name != null ? name : (email != null ? email : phone);
						String customerInfo = customerName + " (Confirmation: " + confirmationCode + ", Guests: " + numberOfGuests + ")";
						
						if (phone != null && !phone.trim().isEmpty()) {
							System.out.println("[Reminder Notification] SMS sent to customer: " + customerInfo + 
							                   " - Reminder: Your reservation is in 2 hours at " + orderTime);
						}
						
						if (email != null && !email.trim().isEmpty()) {
							System.out.println("[Reminder Notification] Email sent to customer: " + customerInfo + 
							                   " - Reminder: Your reservation is in 2 hours at " + orderTime);
						}
						
						// Send QR CODE for subscribers
						if (isSubscriber) {
							System.out.println("[Reminder Notification] QR CODE sent to customer: " + customerInfo + 
							                   " - Reminder: Your reservation is in 2 hours at " + orderTime);
						}
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("[Reminder Notification Thread] Error sending reminder notifications: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("[Reminder Notification Thread] Unexpected error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Start a background thread that periodically sends reminder notifications.
	 * The thread runs every 30 seconds to check for reservations that need reminders
	 * (2 hours before reservation time).
	 */
	private static void startReminderNotificationThread() {
		Thread reminderThread = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(30000);
					if (conn != null && !conn.isClosed()) {
						sendReminderNotifications();
					}
				} catch (InterruptedException e) {
					break;
				} catch (SQLException e) {
					System.err.println("[Reminder Notification Thread] Database connection error: " + e.getMessage());
					e.printStackTrace();
				} catch (Exception e) {
					System.err.println("[Reminder Notification Thread] Unexpected error: " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
		
		reminderThread.setDaemon(true);
		reminderThread.setName("ReminderNotification");
		reminderThread.start();
	}
	
	/**
	 * Check for customers who have been sitting at a table for 2 hours or more.
	 * This method is called periodically by a background thread.
	 */
	private static void checkLongSittingCustomers() {
		try {
			if (conn == null || conn.isClosed()) {
				return;
			}
			
			// Query visit table joined with tables to find customers sitting for 2+ hours
			String sql = "SELECT v.confirmation_code, v.tableID, v.startTime, o.name AS customerName " +
			             "FROM visit v " +
			             "INNER JOIN tables t ON v.confirmation_code = t.confirmationCode " +
			             "LEFT JOIN orders o ON v.confirmation_code = o.confirmation_code " +
			             "WHERE v.endTime IS NULL " +
			             "AND v.startTime <= DATE_SUB(NOW(), INTERVAL 2 HOUR) " +
			             "ORDER BY v.startTime ASC";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql);
			     ResultSet rs = pstmt.executeQuery()) {
				
				boolean foundAny = false;
				while (rs.next()) {
					foundAny = true;
					String confirmationCode = rs.getString("confirmation_code");
					int tableID = rs.getInt("tableID");
					java.sql.Timestamp startTime = rs.getTimestamp("startTime");
					String customerName = rs.getString("customerName");
					
					if (customerName == null || customerName.trim().isEmpty()) {
						customerName = "Unknown";
					}
					
					// Calculate how long they've been sitting
					long startMillis = startTime.getTime();
					long currentMillis = System.currentTimeMillis();
					long sittingMinutes = (currentMillis - startMillis) / (1000 * 60);
					long sittingHours = sittingMinutes / 60;
					long remainingMinutes = sittingMinutes % 60;
					
					System.out.println("[Long Sitting Check] Customer " + customerName + 
					                   " (Confirmation: " + confirmationCode + 
					                   ", Table: " + tableID + 
					                   ") has been sitting for " + sittingHours + " hours and " + remainingMinutes + " minutes");
				}
				
				if (!foundAny) {
					// Optional: print that no long-sitting customers were found (comment out if too verbose)
					// System.out.println("[Long Sitting Check] No customers sitting for 2+ hours");
				}
			}
		} catch (SQLException e) {
			System.err.println("[Long Sitting Check] Database error: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			System.err.println("[Long Sitting Check] Unexpected error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Start a background thread that periodically checks for customers who have been sitting for 2+ hours.
	 * The thread runs every 5 minutes to check for long-sitting customers.
	 */
	private static void startLongSittingCheckThread() {
		Thread longSittingThread = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(300000); // 5 minutes (300000 milliseconds)
					if (conn != null && !conn.isClosed()) {
						checkLongSittingCustomers();
					}
				} catch (InterruptedException e) {
					break;
				} catch (SQLException e) {
					System.err.println("[Long Sitting Check Thread] Database connection error: " + e.getMessage());
					e.printStackTrace();
				} catch (Exception e) {
					System.err.println("[Long Sitting Check Thread] Unexpected error: " + e.getMessage());
					e.printStackTrace();
				}
			}
		});
		
		longSittingThread.setDaemon(true);
		longSittingThread.setName("LongSittingCheck");
		longSittingThread.start();
	}

	/**
	 * Update number of guests for a reservation.
	 * Checks capacity by table size (tables with capacity >= new number of guests) before updating.
	 * @param confirmation_code The confirmation code of the reservation
	 * @param num_of_guests The new number of guests
	 * @return true if updated successfully, false if no capacity or error
	 */
	public static boolean updateNumOfGuests(String confirmation_code, int num_of_guests) {
		// Validate number of guests (must be at least 1)
		if (num_of_guests < 1) {
			return false;
		}
		
		// Check if there are any tables with sufficient capacity
		int tablesWithCapacity = 0;
		try (PreparedStatement checkTablesStmt = conn.prepareStatement(
				"SELECT COUNT(*) FROM tables WHERE capacity >= ?")) {
			checkTablesStmt.setInt(1, num_of_guests);
			try (ResultSet rs = checkTablesStmt.executeQuery()) {
				if (rs.next()) {
					tablesWithCapacity = rs.getInt(1);
				}
			}
		} catch (SQLException e) {
			System.err.println("Error checking table capacity: " + e.getMessage());
			return false;
		}
		
		if (tablesWithCapacity == 0) {
			return false;
		}
		
		try {
			// First, get the current order time to check capacity
			Timestamp orderTime = null;
			try (PreparedStatement getOrderStmt = conn.prepareStatement(
					"SELECT order_time_date FROM orders WHERE confirmation_code = ?")) {
				getOrderStmt.setString(1, confirmation_code);
				try (ResultSet rs = getOrderStmt.executeQuery()) {
					if (rs.next()) {
						orderTime = rs.getTimestamp("order_time_date");
					} else {
						// Order not found
						System.err.println("Error updating number of guests: Order not found");
						return false;
					}
				}
			}
			
			// Check capacity at the reservation time (excluding the current reservation being updated) by new number of guests
			boolean hasCapacity = checkCapacityAtTimeExcludingOrderByGuests(orderTime, confirmation_code, num_of_guests);
			
			if (!hasCapacity) {
				return false;
			}
			
			// Update the number of guests
			try (PreparedStatement stmt = conn.prepareStatement(
					"UPDATE Orders SET number_of_guests = ? WHERE confirmation_code = ?")) {
				stmt.setInt(1, num_of_guests);
				stmt.setString(2, confirmation_code);
				int rowsAffected = stmt.executeUpdate();
				return rowsAffected > 0; // Return true only if at least one row was updated
			}
		} catch (SQLException e) {
			System.err.println("Error updating number of guests: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	

	/**
	 * Check if there's capacity for a reservation at a specific time, excluding a specific order.
	 * Logic:Each reservation  occupies one full table.
	 * Each reservation lasts 2 hours. Count how many reservations overlap in time with the requested time
	 * (excluding the one being updated). If count < total number of tables, approve. Otherwise, reject.
	 * @param requestedDateTime The requested reservation time
	 * @param excludeConfirmationCode The confirmation code of the order to exclude from the count
	 * @param numberOfGuests Number of guests 
	 * @return true if there's capacity, false otherwise
	 */
	private static boolean checkCapacityAtTimeExcludingOrderByGuests(Timestamp requestedDateTime, String excludeConfirmationCode, int numberOfGuests) {
		long twoHoursMillis = 120L * 60L * 1000L; // 2 hours = 120 minutes
		Timestamp reservationEnd = new Timestamp(requestedDateTime.getTime() + twoHoursMillis);

		try {
			// Get all tables sorted by capacity (ascending) - we'll try to assign smallest suitable table first
			List<Integer> allTables = new ArrayList<>();
			List<Integer> tableCapacities = new ArrayList<>();
			try (PreparedStatement tablesStmt = conn.prepareStatement(
					"SELECT tableID, capacity FROM tables ORDER BY capacity ASC")) {
				try (ResultSet rs = tablesStmt.executeQuery()) {
					while (rs.next()) {
						allTables.add(rs.getInt("tableID"));
						tableCapacities.add(rs.getInt("capacity"));
					}
				}
			}
			
			if (allTables.isEmpty()) {
				return false; // No tables available
			}
			
			// Get all overlapping reservations with their number_of_guests (excluding the one being updated)
			// A reservation overlaps if: it starts before the requested reservation ends AND it ends after the requested reservation starts
			String getReservationsSql =
					"SELECT number_of_guests " +
					"FROM orders o " +
					"WHERE o.order_time_date < ? " +
					"AND DATE_ADD(o.order_time_date, INTERVAL 120 MINUTE) > ? " +
					"AND o.confirmation_code != ? " +
					"AND o.status NOT IN ('cancelled', 'Cancelled by user', 'Cancelled by resturant', 'paid') " +
					"ORDER BY number_of_guests DESC";
			
			List<Integer> overlappingReservations = new ArrayList<>();
			try (PreparedStatement resStmt = conn.prepareStatement(getReservationsSql)) {
				resStmt.setTimestamp(1, reservationEnd);
				resStmt.setTimestamp(2, requestedDateTime);
				resStmt.setString(3, excludeConfirmationCode);
				try (ResultSet rs = resStmt.executeQuery()) {
					while (rs.next()) {
						overlappingReservations.add(rs.getInt("number_of_guests"));
					}
				}
			}
			
			// Simulate table assignment using greedy algorithm:
			// Assign each reservation (including the updated one) to the smallest table that can accommodate it
			// Sort reservations by number of guests (descending) to assign larger groups first
			List<Integer> reservationsToAssign = new ArrayList<>(overlappingReservations);
			reservationsToAssign.add(numberOfGuests); // Add the updated reservation
			reservationsToAssign.sort((a, b) -> Integer.compare(b, a)); // Sort descending
			
			// Track which tables are already assigned
			boolean[] tablesAssigned = new boolean[allTables.size()];
			
			// Try to assign each reservation
			for (int guests : reservationsToAssign) {
				boolean assigned = false;
				// Find the smallest unassigned table that can accommodate this reservation
				for (int i = 0; i < allTables.size(); i++) {
					if (!tablesAssigned[i] && tableCapacities.get(i) >= guests) {
						tablesAssigned[i] = true;
						assigned = true;
						break;
					}
				}
				if (!assigned) {
					// Cannot assign this reservation - no capacity
					return false;
				}
			}
			
			// All reservations (including the updated one) can be assigned
			return true;
		} catch (SQLException e) {
			System.err.println("Error during capacity check (excluding order by guests): " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * This method is getting information to change in the DB 
	 * Added capacity check before updating - same logic as insertReservation
	 * @param confirmation_code	- confirmation code to identify the reservation
	 * @param new_date	- new date to change
	 * @return "Updated" if success, "NO_CAPACITY" if no capacity, "Error" if error
	 */
	public static String updateOrderDate(String confirmation_code, Timestamp new_date) {
		// Get the current order_time_date and number_of_guests to check if it's the same as new_date and check capacity
		Timestamp currentDate = null;
		int numberOfGuests = 0;
		try (PreparedStatement stmt = conn.prepareStatement(
				"SELECT order_time_date, number_of_guests FROM orders WHERE confirmation_code = ?")) {
			stmt.setString(1, confirmation_code);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					currentDate = rs.getTimestamp("order_time_date");
					numberOfGuests = rs.getInt("number_of_guests");
				} else {
					// Order not found
					return "Error";
				}
			}
		} catch (SQLException e) {
			System.err.println("Error fetching current order date: " + e.getMessage());
			e.printStackTrace();
			return "Error";
		}
		
		// If the date is the same, no need to check capacity (it's already there)
		if (currentDate != null && currentDate.equals(new_date)) {
			// Still need to update (even though it's the same) to return success
		} else {
			// Check capacity at the new time (excluding the current reservation being updated) by number of guests
			boolean hasCapacity = checkCapacityAtTimeExcludingOrderByGuests(new_date, confirmation_code, numberOfGuests);
			
			if (!hasCapacity) {
				// Find alternative times
				String altTimes = findAlternativeTimes(new_date, numberOfGuests);
				if (!altTimes.isEmpty()) {
					return "NO_CAPACITY:" + altTimes;
				}
				return "NO_CAPACITY:NO_ALT_TIMES";
			}
		}
		
		try (PreparedStatement stmt = conn.prepareStatement(
				"UPDATE Orders SET order_time_date = ? WHERE confirmation_code = ?"
			)) {
			stmt.setTimestamp(1, new_date); 
			stmt.setString(2, confirmation_code);
			int rowsAffected = stmt.executeUpdate();
			if (rowsAffected > 0) {
				return "Updated";
			} else {
				return "Error";
			}
		} catch (SQLException e) {
			System.err.println("Error updating order date: " + e.getMessage());
			e.printStackTrace();
			return "Error";
		}
	}
	
	public static String CheckIn(String confirmationCode) {
		try {
			String orderQuery = "SELECT order_number, number_of_guests, email, phone, order_time_date, subscriber_id, status " +
			                    "FROM orders WHERE confirmation_code = ?";
			String orderNumber = null;
			int guests = 0;
			String email = null;
			String phone = null;
			Timestamp orderDateTime = null;
			Integer subscriberId = null;
			boolean isFromWaitingList = false;

			try (PreparedStatement orderStmt = conn.prepareStatement(orderQuery)) {
				orderStmt.setString(1, confirmationCode);

				try (ResultSet rs = orderStmt.executeQuery()) {
					if (rs.next()) {
						// Found in orders table - check if status is PENDING
						String status = rs.getString("status");
						if (status == null || !status.equalsIgnoreCase("PENDING")) {
							return "CheckInFailed:Reservation status is not PENDING. Current status: " + (status != null ? status : "unknown");
						}
						
						orderDateTime = rs.getTimestamp("order_time_date");
						
						// Check if reservation date matches today's date
						if (orderDateTime != null) {
							java.time.LocalDate reservationDate = orderDateTime.toLocalDateTime().toLocalDate();
							java.time.LocalDate today = java.time.LocalDate.now();
							if (!reservationDate.equals(today)) {
								return "CheckInFailed:Reservation date does not match today's date. Reservation date: " + reservationDate + ", Today: " + today;
							}
							
							// Check if customer is 15+ minutes late - if so, cancel reservation and deny check-in
							java.time.Instant reservationTime = orderDateTime.toInstant();
							java.time.Instant currentTime = java.time.Instant.now();
							long delayMinutes = java.time.Duration.between(reservationTime, currentTime).toMinutes();
							
							if (delayMinutes >= 15) {
								// Cancel the reservation
								String cancelSql = "UPDATE orders SET status = 'Cancelled by resturant' WHERE confirmation_code = ?";
								try (PreparedStatement cancelStmt = conn.prepareStatement(cancelSql)) {
									cancelStmt.setString(1, confirmationCode);
									cancelStmt.executeUpdate();
								}
								return "CheckInFailed:Reservation cancelled - Customer is " + delayMinutes + " minutes late. Reservations are automatically cancelled if customer is 15+ minutes late.";
							}
						}
						
						orderNumber = rs.getString("order_number");
						guests = rs.getInt("number_of_guests");
						email = rs.getString("email");
						phone = rs.getString("phone");
						Object subIdObj = rs.getObject("subscriber_id");
						if (subIdObj != null) {
							subscriberId = rs.getInt("subscriber_id");
						}
			} else {
						String waitingQuery = "SELECT number_of_guests, phone, email " +
						                      "FROM waitingentry WHERE confirmation_code = ? AND status IN ('WAITING', 'P_WAITING')";
						try (PreparedStatement waitingStmt = conn.prepareStatement(waitingQuery)) {
							waitingStmt.setString(1, confirmationCode);
							try (ResultSet waitingRs = waitingStmt.executeQuery()) {
								if (waitingRs.next()) {
									// Found in waitingentry
									isFromWaitingList = true;
									orderNumber = null; // No order_number for waiting list entries
									guests = waitingRs.getInt("number_of_guests");
									email = waitingRs.getString("email");
									phone = waitingRs.getString("phone");
									orderDateTime = null; // No reservation time for waiting list
								} else {
									return "CheckInFailed:OrderNotFound";
								}
							}
						}
					}
				}
			}

			String tableQuery =
				"SELECT tableID, capacity " +
				"FROM tables " +
				"WHERE tableStatus = 'AVAILABLE' AND capacity >= ? " +
				"ORDER BY capacity ASC " +
				"LIMIT 1";

			Integer tableId = null;

			try (PreparedStatement tableStmt = conn.prepareStatement(tableQuery)) {
				tableStmt.setInt(1, guests);
				try (ResultSet rs = tableStmt.executeQuery()) {
					if (rs.next()) {
						tableId = rs.getInt("tableID");
					}
				}
			}

			if (tableId == null) {
				try {
					String checkWaitingSql = "SELECT waitingID FROM waitingentry WHERE confirmation_code = ? AND status IN ('WAITING', 'P_WAITING')";
					boolean alreadyInWaitingList = false;
					try (PreparedStatement checkStmt = conn.prepareStatement(checkWaitingSql)) {
						checkStmt.setString(1, confirmationCode);
						try (ResultSet rs = checkStmt.executeQuery()) {
							if (rs.next()) {
								alreadyInWaitingList = true;
							}
						}
					}
					
					if (!alreadyInWaitingList) {
						java.time.LocalDate today = java.time.LocalDate.now();
						String insertWaitingSql = "INSERT INTO waitingentry (number_of_guests, phone, email, date, confirmation_code, status) VALUES (?, ?, ?, ?, ?, ?)";
						
						try (PreparedStatement insertStmt = conn.prepareStatement(insertWaitingSql)) {
							insertStmt.setInt(1, guests);
							insertStmt.setString(2, phone);
							insertStmt.setString(3, email);
							insertStmt.setDate(4, java.sql.Date.valueOf(today));
							insertStmt.setString(5, confirmationCode);
							insertStmt.setString(6, "P_WAITING");
							insertStmt.executeUpdate();
						}
					} else {
						String updateStatusSql = "UPDATE waitingentry SET status = 'P_WAITING' WHERE confirmation_code = ? AND status = 'WAITING'";
						try (PreparedStatement updateStmt = conn.prepareStatement(updateStatusSql)) {
							updateStmt.setString(1, confirmationCode);
							updateStmt.executeUpdate();
						}
					}
					
					return "NoTableAvailable:AddedToWaitingList";
				} catch (SQLException e) {
					System.err.println("Error adding customer to waiting list during check-in: " + e.getMessage());
					e.printStackTrace();
					return "NoTableAvailable:Error";
				}
			}

			conn.setAutoCommit(false);
			try {
				if (isFromWaitingList) {
					// Update waitingentry status to "SEATED" (or remove from waiting list)
					String updateWaitingSql = "UPDATE waitingentry SET status = 'SEATED' WHERE confirmation_code = ?";
					try (PreparedStatement updateWaiting = conn.prepareStatement(updateWaitingSql)) {
						updateWaiting.setString(1, confirmationCode);
						updateWaiting.executeUpdate();
					}
				} else {
					// Update order status to CheckedIN
					String updateOrderSql = "UPDATE orders SET status = ? WHERE confirmation_code = ?";
					try (PreparedStatement updateOrder = conn.prepareStatement(updateOrderSql)) {
						updateOrder.setString(1, "CheckedIN");
						updateOrder.setString(2, confirmationCode);
						updateOrder.executeUpdate();
					}
				}

				String updateTableSql = "UPDATE tables SET tableStatus = ?, confirmationCode = ? WHERE tableID = ?";
				try (PreparedStatement updateTable = conn.prepareStatement(updateTableSql)) {
					updateTable.setString(1, "OCCUPIED");
					updateTable.setString(2, confirmationCode);
					updateTable.setInt(3, tableId);
					updateTable.executeUpdate();
				}

				String insertVisitSql =
					"INSERT INTO visit (order_number, confirmation_code, tableID, startTime, subId) " +
					"VALUES (?, ?, ?, NOW(), ?)";
				try (PreparedStatement insertVisit = conn.prepareStatement(insertVisitSql)) {
					insertVisit.setString(1, orderNumber);
					insertVisit.setString(2, confirmationCode);
					insertVisit.setInt(3, tableId);
					if (!isFromWaitingList && subscriberId != null) {
						insertVisit.setInt(4, subscriberId);
					} else {
						insertVisit.setNull(4, java.sql.Types.INTEGER);
					}
					insertVisit.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
					throw e; // Re-throw to trigger rollback
				}

				conn.commit();
				conn.setAutoCommit(true);

				String msg = "Your table is ready. Table: " + tableId;
				System.out.println(msg + " (email=" + email + ", phone=" + phone + ")");

				return "CheckInSuccess:TABLE=" + tableId;

			} catch (SQLException e) {
				conn.rollback();
				conn.setAutoCommit(true);
				System.err.println("Error during check-in transaction: " + e.getMessage());
				e.printStackTrace();
				return "CheckInFailed:ServerError";
			}

		} catch (SQLException e) {
			System.err.println("Error during check-in: " + e.getMessage());
			e.printStackTrace();
			return "CheckInFailed:ServerError";
		}
	}
	


	public static String LostCode(String emailOrPhone) {
	    PreparedStatement stmt = null;
	    ResultSet rs = null;

	    try {
	        String query =
	            "SELECT confirmation_code, status, email, phone, name " +
	            "FROM orders " +
	            "WHERE (email = ? OR phone = ?) " +
	            "AND DATE(order_time_date) = CURRENT_DATE " +
	            "AND status = 'PENDING' " +
	            "ORDER BY order_time_date DESC " +
	            "LIMIT 1";

	        stmt = conn.prepareStatement(query);
	        stmt.setString(1, emailOrPhone);
	        stmt.setString(2, emailOrPhone);

	        rs = stmt.executeQuery();

	        if (rs.next()) {
	            String code = rs.getString("confirmation_code");
	            String email = rs.getString("email");
	            String phone = rs.getString("phone");
	            String name = rs.getString("name");
	            
	            String customerInfo = name != null ? name : (email != null ? email : phone);
	            
	            if (phone != null && !phone.trim().isEmpty()) {
	                System.out.println("[Lost Code] SMS sent to customer: " + customerInfo + 
	                                   " (Phone: " + phone + ") - Confirmation code: " + code);
	            }
	            
	            if (email != null && !email.trim().isEmpty()) {
	                System.out.println("[Lost Code] Email sent to customer: " + customerInfo + 
	                                   " (Email: " + email + ") - Confirmation code: " + code);
	            }
	            
	            return "CodeSent";
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
	 * Inserts a waiting list entry for a subscriber.
	 * Uses subscriberID to fetch phone/email from subscriber table.
	 * First checks if restaurant has available capacity in the next 2 hours.
	 * If restaurant is NOT full in the next 2 hours (has capacity), performs automatic check-in:
	 * creates an order in orders table with current time, updates status to CheckedIN, assigns a table,
	 * marks table as OCCUPIED, and creates visit record. Returns "TABLE_AVAILABLE:<tableID>".
	 * If restaurant is full, creates confirmation code and adds to waiting list, returns "WAITING_LIST:<confirmation_code>".
	 * @return "TABLE_AVAILABLE:<tableID>" if restaurant has capacity (automatic check-in completed), "WAITING_LIST:<confirmation_code>" if added to waiting list, "Error" on failure
	 */
	public static String insertWaitingEntryForSubscriber(String subscriberIdStr, int numberOfGuests) {
		if (subscriberIdStr == null) return "Error";
		
		try {
			int subscriberId = Integer.parseInt(subscriberIdStr);
			
			String email = null;
			String phone = null;
			String name = null;
			
			// Fetch subscriber contact info
			String subQuery = "SELECT name, email, phone FROM subscriber WHERE subscriberID = ?";
			try (PreparedStatement subStmt = conn.prepareStatement(subQuery)) {
				subStmt.setInt(1, subscriberId);
				try (ResultSet rs = subStmt.executeQuery()) {
					if (rs.next()) {
						name = rs.getString("name");
						email = rs.getString("email");
						phone = rs.getString("phone");
					} else {
						return "Error"; // no such subscriber
					}
				}
			}
			
			Timestamp currentTime = new Timestamp(System.currentTimeMillis());
			
			// Check if restaurant has capacity in the next 2 hours (not full)
			boolean hasCapacity = checkCapacityAtTimeByGuests(currentTime, numberOfGuests);
			
			if (hasCapacity) {
				// Restaurant is NOT full in the next 2 hours - create order and assign table
				try {
					// Generate confirmation code
					String confirmationCode = generateConfirmationCode();
					
					// Generate order number (can be same as confirmation code or different)
					String orderNumber = confirmationCode;
					
					// Find an available table that can accommodate the number of guests
					String tableQuery =
						"SELECT tableID, capacity " +
						"FROM tables " +
						"WHERE tableStatus = 'AVAILABLE' AND capacity >= ? " +
						"ORDER BY capacity ASC " +
						"LIMIT 1";
					
					Integer tableId = null;
					
					try (PreparedStatement tableStmt = conn.prepareStatement(tableQuery)) {
						tableStmt.setInt(1, numberOfGuests);
						try (ResultSet rs = tableStmt.executeQuery()) {
							if (rs.next()) {
								tableId = rs.getInt("tableID");
							}
						}
					}
					
					if (tableId != null) {
						conn.setAutoCommit(false);
						try {
							String insertOrderSql =
								"INSERT INTO orders " +
								"(subscriber_id, number_of_guests, confirmation_code, order_number, " +
								"order_time_date, time_date_of_placing_order, status, is_subscriber, email, phone, name) " +
								"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
							
							try (PreparedStatement orderStmt = conn.prepareStatement(insertOrderSql)) {
								orderStmt.setInt(1, subscriberId);
								orderStmt.setInt(2, numberOfGuests);
								orderStmt.setString(3, confirmationCode);
								orderStmt.setString(4, orderNumber);
								orderStmt.setTimestamp(5, currentTime);
								orderStmt.setTimestamp(6, currentTime);
								orderStmt.setString(7, "PENDING");
								orderStmt.setBoolean(8, true);
								orderStmt.setString(9, email);
								orderStmt.setString(10, phone);
								orderStmt.setString(11, name);
								orderStmt.executeUpdate();
							}
							
							String updateOrderSql = "UPDATE orders SET status = ? WHERE confirmation_code = ?";
							try (PreparedStatement updateOrder = conn.prepareStatement(updateOrderSql)) {
								updateOrder.setString(1, "CheckedIN");
								updateOrder.setString(2, confirmationCode);
								updateOrder.executeUpdate();
							}
							
							String updateTableSql = "UPDATE tables SET tableStatus = ?, confirmationCode = ? WHERE tableID = ?";
							try (PreparedStatement updateTable = conn.prepareStatement(updateTableSql)) {
								updateTable.setString(1, "OCCUPIED");
								updateTable.setString(2, confirmationCode);
								updateTable.setInt(3, tableId);
								updateTable.executeUpdate();
							}
							
							String insertVisitSql =
								"INSERT INTO visit (order_number, confirmation_code, tableID, startTime, subId) " +
								"VALUES (?, ?, ?, NOW(), ?)";
							try (PreparedStatement insertVisit = conn.prepareStatement(insertVisitSql)) {
								insertVisit.setString(1, orderNumber);
								insertVisit.setString(2, confirmationCode);
								insertVisit.setInt(3, tableId);
								insertVisit.setInt(4, subscriberId);
								insertVisit.executeUpdate();
							}
							
							conn.commit();
							conn.setAutoCommit(true);
							
							return "TABLE_AVAILABLE:" + tableId;
							
						} catch (SQLException e) {
							conn.rollback();
							conn.setAutoCommit(true);
							System.err.println("Error performing automatic check-in for subscriber waiting list entry: " + e.getMessage());
							e.printStackTrace();
							// Fall through to waiting list
						}
					}
				} catch (SQLException e) {
					System.err.println("Error processing subscriber waiting list entry with capacity: " + e.getMessage());
					e.printStackTrace();
					// If error occurs, fall through to waiting list
				}
			}
			
			// Restaurant is full in the next 2 hours - generate confirmation code and add to waiting list (customer must wait)
			System.out.println("Restaurant is full - adding subscriber " + subscriberIdStr + " (" + numberOfGuests + " guests) to waiting list");
			String confirmationCode = generateConfirmationCode();
			
			// Insert into waitingentry for today's date
			java.time.LocalDate today = java.time.LocalDate.now();
			String sql = "INSERT INTO waitingentry (number_of_guests, phone, email, date, confirmation_code, status) VALUES (?, ?, ?, ?, ?, ?)";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, numberOfGuests);
				pstmt.setString(2, phone);
				pstmt.setString(3, email);
				pstmt.setDate(4, java.sql.Date.valueOf(today));
				pstmt.setString(5, confirmationCode);
				pstmt.setString(6, "WAITING"); // Default status: WAITING (low priority)
				
				pstmt.executeUpdate();
				return "WAITING_LIST:" + confirmationCode;
			}
			
		} catch (NumberFormatException e) {
			System.err.println("Invalid subscriber ID for waiting list: " + subscriberIdStr);
			return "Error";
		} catch (SQLException e) {
			System.err.println("Error inserting waiting entry for subscriber: " + e.getMessage());
			e.printStackTrace();
			return "Error";
		}
	}




	/**
	 * This method is getting order by confirmation code and returning String of its data
	 * FIX: Changed to use confirmation_code instead of order_number
	 * @param confirmation_code	- confirmation code
	 * @return String of this order
	 *
	 * Format:
	 * order_number, order_time_date, number_of_guests, confirmation_code,
	 * subscriber_id, time_date_of_placing_order, email, phone
	 */
	public static String Load(String confirmation_code) {
		String query = "SELECT * FROM Orders WHERE confirmation_code = ?";
		String orderData = new String("Empty");
		try (PreparedStatement stmt = conn.prepareStatement(query)) {
			stmt.setString(1, confirmation_code);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					String order_num1 = rs.getString("order_number");
					Timestamp date = rs.getTimestamp("order_time_date");
					int num_guests = rs.getInt("number_of_guests");
					String con_code = rs.getString("confirmation_code");
					Integer sub_id = rs.getObject("subscriber_id") != null ? rs.getInt("subscriber_id") : null;
					Timestamp date_placing_order = rs.getTimestamp("time_date_of_placing_order");
					String email = rs.getString("email");
					String phone = rs.getString("phone");
					String status = rs.getString("status");

					// Create a formatted string with the order information including contact and status
					orderData = order_num1 + ", " + date + ", " + num_guests + ", " + con_code + ", " +
					            sub_id + ", " + date_placing_order + ", " + email + ", " + phone + ", " + status;
					return orderData;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return orderData;

	}

	/**
	 * Updates either email or phone (or both) for an order identified by confirmation code.
	 * If newContact looks like email → updates email and clears phone.
	 * If numeric phone → updates phone and clears email.
	 */
	public static boolean updateEmailOrPhone(String confirmation_code, String newContact) {
		if (newContact == null || newContact.trim().isEmpty()) {
			return false;
		}

		newContact = newContact.trim();

		boolean isEmail = newContact.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
		boolean isPhone = newContact.matches("\\d{8,15}");

		String sql;

		if (isEmail) {
			sql = "UPDATE Orders SET email = ?, phone = NULL WHERE confirmation_code = ?";
		} else if (isPhone) {
			sql = "UPDATE Orders SET phone = ?, email = NULL WHERE confirmation_code = ?";
		} else {
			// Invalid format
			return false;
		}

		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, newContact);
			stmt.setString(2, confirmation_code);

			int rowsAffected = stmt.executeUpdate();
			return rowsAffected > 0;
		} catch (SQLException e) {
			System.err.println("Error updating email/phone: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Gets subscriber visit history by subscriber ID.
	 * Returns list of visit records with created_at, startTime, confirmation_code, number_of_guests, and finalAmount from bill.
	 * @param subscriberID The subscriber ID
	 * @return List<String> where each string is in format "created_at|startTime|confirmation_code|number_of_guests|finalAmount"
	 */
	public static List<String> getSubscriberHistory(String subscriberID) {
		List<String> history = new ArrayList<>();
		
		if (subscriberID == null || subscriberID.trim().isEmpty()) {
			return history;
		}

		try {
			int subId = Integer.parseInt(subscriberID.trim());
			// Query visit table joined with orders table to get number_of_guests and bill table to get finalAmount
			String sql = 
				"SELECT v.created_at, v.startTime, v.confirmation_code, " +
				"COALESCE(o.number_of_guests, NULL) as number_of_guests, " +
				"COALESCE(b.finalAmount, NULL) as finalAmount " +
				"FROM visit v " +
				"LEFT JOIN orders o ON v.confirmation_code = o.confirmation_code " +
				"LEFT JOIN bill b ON v.confirmation_code = b.confirmation_code " +
				"WHERE v.subId = ? " +
				"ORDER BY v.created_at DESC";
			
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setInt(1, subId);
				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						String createdAt = rs.getTimestamp("created_at") != null ? 
							rs.getTimestamp("created_at").toString() : "";
						String startTime = rs.getTimestamp("startTime") != null ? 
							rs.getTimestamp("startTime").toString() : "";
						String confirmationCode = rs.getString("confirmation_code");
						confirmationCode = confirmationCode != null ? confirmationCode : "";
						
						// Get number_of_guests - can be NULL if no order exists
						Integer numberOfGuests = rs.getObject("number_of_guests") != null ? 
							rs.getInt("number_of_guests") : null;
						String guestsStr = numberOfGuests != null ? numberOfGuests.toString() : "";
						
						// Get finalAmount - can be NULL if no bill exists
						java.math.BigDecimal finalAmount = rs.getBigDecimal("finalAmount");
						String amountStr = finalAmount != null ? finalAmount.toString() : "";
						
						// Format: "created_at|startTime|confirmation_code|number_of_guests|finalAmount"
						String record = createdAt + "|" + startTime + "|" + confirmationCode + "|" + guestsStr + "|" + amountStr;
						history.add(record);
					}
				}
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid subscriber ID format: " + subscriberID);
		} catch (SQLException e) {
			System.err.println("Error getting subscriber history: " + e.getMessage());
			e.printStackTrace();
		}
		
		return history;
	}

	/**
	 * Gets subscriber information (name, email, phone) by subscriber ID.
	 * @param subscriberID The subscriber ID
	 * @return String in format "name|email|phone" or "Error" if not found
	 */
	public static String getSubscriberInfo(String subscriberID) {
		if (subscriberID == null || subscriberID.trim().isEmpty()) {
			return "Error";
		}

		try {
			int subId = Integer.parseInt(subscriberID.trim());
			String sql = "SELECT name, email, phone FROM subscriber WHERE subscriberID = ?";
			
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setInt(1, subId);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						String name = rs.getString("name");
						String email = rs.getString("email");
						String phone = rs.getString("phone");
						
						// Handle null values
						name = name != null ? name : "";
						email = email != null ? email : "";
						phone = phone != null ? phone : "";
						
						return name + "|" + email + "|" + phone;
					} else {
						return "Error";
					}
				}
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid subscriber ID format: " + subscriberID);
			return "Error";
		} catch (SQLException e) {
			System.err.println("Error getting subscriber info: " + e.getMessage());
			e.printStackTrace();
			return "Error";
		}
	}

	/**
	 * Updates subscriber information (name, email, phone) by subscriber ID.
	 * @param subscriberID The subscriber ID
	 * @param name The new name
	 * @param email The new email (can be empty)
	 * @param phone The new phone (can be empty)
	 * @return true if successful, false otherwise
	 */
	public static boolean updateSubscriberInfo(String subscriberID, String name, String email, String phone) {
		if (subscriberID == null || subscriberID.trim().isEmpty() || name == null || name.trim().isEmpty()) {
			return false;
		}

		try {
			int subId = Integer.parseInt(subscriberID.trim());
			String sql = "UPDATE subscriber SET name = ?, email = ?, phone = ? WHERE subscriberID = ?";
			
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, name.trim());
				// Set email - use null if empty
				if (email != null && !email.trim().isEmpty()) {
					stmt.setString(2, email.trim());
				} else {
					stmt.setNull(2, java.sql.Types.VARCHAR);
				}
				// Set phone - use null if empty
				if (phone != null && !phone.trim().isEmpty()) {
					stmt.setString(3, phone.trim());
				} else {
					stmt.setNull(3, java.sql.Types.VARCHAR);
				}
				stmt.setInt(4, subId);

				int rowsAffected = stmt.executeUpdate();
				return rowsAffected > 0;
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid subscriber ID format: " + subscriberID);
			return false;
		} catch (SQLException e) {
			System.err.println("Error updating subscriber info: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
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
				String order_num1 = rs.getString("order_number");
				Timestamp date = rs.getTimestamp("order_time_date");
				int num_guests = rs.getInt("number_of_guests");
				String con_code = rs.getString("confirmation_code");
				Integer sub_id = rs.getObject("subscriber_id") != null ? rs.getInt("subscriber_id") : null;
				Timestamp date_placing_order = rs.getTimestamp("time_date_of_placing_order");

				// Create a formatted string with the subscriber's information
				String orderData = order_num1 + ", " + date + ", " + num_guests + ", " + con_code + ", " + sub_id + ", " + date_placing_order;

				// Add the formatted string to the list
				orders.add(orderData);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return orders;
	}
	
	
	/**
	 * Check if there's capacity for a reservation at a specific time.
	 * Logic: Check the tables table to find tables with capacity >= numberOfGuests.
	 * Each reservation occupies one full table (even if number of guests < table capacity).
	 * Each reservation lasts 2 hours. Count how many reservations overlap in time with the requested time.
	 * Two reservations overlap if one starts before the other ends, and one ends after the other starts.
	 * If there are available tables with sufficient capacity, approve. Otherwise, reject.
	 * @param requestedDateTime The requested reservation time
	 * @param numberOfGuests Number of guests (must find a table with capacity >= numberOfGuests)
	 * @return true if there's capacity, false otherwise
	 */
	private static boolean checkCapacityAtTimeByGuests(Timestamp requestedDateTime, int numberOfGuests) {
		long twoHoursMillis = 120L * 60L * 1000L; // 2 hours = 120 minutes
		Timestamp reservationEnd = new Timestamp(requestedDateTime.getTime() + twoHoursMillis);

		System.out.println("[DEBUG checkCapacityAtTimeByGuests] Called with numberOfGuests=" + numberOfGuests + ", requestedDateTime=" + requestedDateTime);

		try {
			// Get all tables sorted by capacity (ascending) - we'll try to assign smallest suitable table first
			List<Integer> allTables = new ArrayList<>();
			List<Integer> tableCapacities = new ArrayList<>();
			try (PreparedStatement tablesStmt = conn.prepareStatement(
					"SELECT tableID, capacity FROM tables ORDER BY capacity ASC")) {
				try (ResultSet rs = tablesStmt.executeQuery()) {
					while (rs.next()) {
						allTables.add(rs.getInt("tableID"));
						tableCapacities.add(rs.getInt("capacity"));
					}
				}
			}
			
			if (allTables.isEmpty()) {
				return false; // No tables available
			}
			
			// Get all overlapping reservations with their number_of_guests and confirmation_code for debugging
			// A reservation overlaps if: it starts before the requested reservation ends AND it ends after the requested reservation starts
			String getReservationsSql =
					"SELECT number_of_guests, confirmation_code, status " +
					"FROM orders o " +
					"WHERE o.order_time_date < ? " +
					"AND DATE_ADD(o.order_time_date, INTERVAL 120 MINUTE) > ? " +
					"AND o.status NOT IN ('cancelled', 'Cancelled by user', 'Cancelled by resturant', 'paid') " +
					"ORDER BY number_of_guests DESC";
			
			List<Integer> overlappingReservations = new ArrayList<>();
			List<String> overlappingConfirmations = new ArrayList<>();
			try (PreparedStatement resStmt = conn.prepareStatement(getReservationsSql)) {
				resStmt.setTimestamp(1, reservationEnd);
				resStmt.setTimestamp(2, requestedDateTime);
				try (ResultSet rs = resStmt.executeQuery()) {
					while (rs.next()) {
						int guests = rs.getInt("number_of_guests");
						String confCode = rs.getString("confirmation_code");
						String status = rs.getString("status");
						overlappingReservations.add(guests);
						overlappingConfirmations.add(confCode + "(" + status + ")");
					}
				}
			}
			
			System.out.println("[DEBUG checkCapacityAtTimeByGuests] Found " + overlappingReservations.size() + " overlapping reservations: " + overlappingConfirmations);
			System.out.println("[DEBUG checkCapacityAtTimeByGuests] Overlapping reservation guests: " + overlappingReservations);
			
			// Simulate table assignment using greedy algorithm:
			// Assign each reservation (including the new one) to the smallest table that can accommodate it
			// Sort reservations by number of guests (descending) to assign larger groups first
			List<Integer> reservationsToAssign = new ArrayList<>(overlappingReservations);
			reservationsToAssign.add(numberOfGuests); // Add the new reservation
			reservationsToAssign.sort((a, b) -> Integer.compare(b, a)); // Sort descending
			
			System.out.println("[DEBUG checkCapacityAtTimeByGuests] After adding new reservation (" + numberOfGuests + " guests), total reservations to assign: " + reservationsToAssign);
			
			// Track which tables are already assigned
			boolean[] tablesAssigned = new boolean[allTables.size()];
			
			// Try to assign each reservation
			for (int guests : reservationsToAssign) {
				boolean assigned = false;
				// Find the smallest unassigned table that can accommodate this reservation
				for (int i = 0; i < allTables.size(); i++) {
					if (!tablesAssigned[i] && tableCapacities.get(i) >= guests) {
						tablesAssigned[i] = true;
						assigned = true;
						break;
					}
				}
				if (!assigned) {
					// Cannot assign this reservation - no capacity
					return false;
				}
			}
			
			// All reservations (including the new one) can be assigned
			return true;
		} catch (SQLException e) {
			System.err.println("Error during capacity check by guests: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Calculate how many tables are available at a specific time
	 * @param checkTime The time to check
	 * @param reservationStartTimes List of reservation start times
	 * @param reservationEndTimes List of reservation end times (start + 120 minutes = 2 hours)
	 * @param visitStartTimes List of visit start times
	 * @param visitEndTimes List of visit end times
	 * @param totalTables Total number of tables in restaurant
	 * @return Number of available tables at checkTime
	 */
	private static int calculateAvailableTablesAtTime(Timestamp checkTime, 
			List<Timestamp> reservationStartTimes, List<Timestamp> reservationEndTimes,
			List<Timestamp> visitStartTimes, List<Timestamp> visitEndTimes,
			int totalTables) {
		
		int occupied = 0;
		long twoHoursMillis = 120L * 60L * 1000L; // 2 hours = 120 minutes
		Timestamp checkTimeEnd = new Timestamp(checkTime.getTime() + twoHoursMillis);
		
		// Count reservations that occupy tables at checkTime
		// A reservation at checkTime will occupy a table from checkTime to checkTimeEnd
		// Another reservation overlaps if: its start < checkTimeEnd AND its end > checkTime
		// BUT: if end == checkTimeEnd exactly, they don't overlap (one ends when the other starts - table can be reused)
		int reservationOverlaps = 0;
		for (int i = 0; i < reservationStartTimes.size(); i++) {
			Timestamp start = reservationStartTimes.get(i);
			Timestamp end = reservationEndTimes.get(i);
			
			// Reservation overlaps with checkTime if: start < checkTimeEnd AND end > checkTime
			// Special case: If end == checkTime, they DON'T overlap (table freed exactly when new reservation starts)
			// Special case: If start == checkTime and end == checkTimeEnd, they overlap (same time slot)
			boolean overlaps = false;
			
			if (start.equals(checkTime) && end.equals(checkTimeEnd)) {
				// Same time slot - overlaps
				overlaps = true;
			} else if (end.equals(checkTime)) {
				// Reservation ends exactly when ours starts - doesn't overlap
				overlaps = false;
			} else if (start.before(checkTimeEnd) && end.after(checkTime)) {
				// Standard overlap check
				overlaps = true;
			}
			
			if (overlaps) {
				reservationOverlaps++;
			}
		}
		occupied += reservationOverlaps;
		
		// Count visits that occupy tables at checkTime
		for (int i = 0; i < visitStartTimes.size(); i++) {
			Timestamp start = visitStartTimes.get(i);
			Timestamp end = visitEndTimes.get(i);
			
			boolean overlaps = start.before(checkTimeEnd) && end.after(checkTime);
			if (overlaps) {
				occupied++;
			}
		}
		
		int available = totalTables - occupied;
		
		return available;
	}

	/**
	 * Find alternative times for a reservation (before and after requested time)
	 * Logic: Check capacity at each time using checkCapacityAtTimeByGuests to ensure
	 * there are enough tables for the specific number of guests
	 * @param requestedDateTime The requested reservation time
	 * @param numberOfGuests The number of guests for the reservation
	 * @return String with alternative times in format: "ALT_TIMES:beforeTime|afterTime" or empty if none found
	 */
	private static String findAlternativeTimes(Timestamp requestedDateTime, int numberOfGuests) {
		try {

			// Find BEFORE time: check when tables are freed (when reservations end)
			// We need to find the latest time before requested time when we can start a reservation
			// Logic: Check capacity at each time slot going backwards from requested time
			// A reservation at checkTime ends at checkTime + 2 hours, so we need to ensure no overlap
			Timestamp beforeTime = null;
			
			long thirtyMinutesMillis = 30L * 60L * 1000L;
			
			// Round requested time down to nearest 30 minutes
			long roundedRequested = (requestedDateTime.getTime() / thirtyMinutesMillis) * thirtyMinutesMillis;
			
			for (long checkTimeMillis = roundedRequested - thirtyMinutesMillis; 
				 checkTimeMillis >= roundedRequested - (4L * 60L * 60L * 1000L); 
				 checkTimeMillis -= thirtyMinutesMillis) {
				
				Timestamp checkTime = new Timestamp(checkTimeMillis);
				
				if (!canMakeReservationAtTime(checkTime)) {
					continue;
				}
				
				boolean hasCapacity = checkCapacityAtTimeByGuests(checkTime, numberOfGuests);
				
				if (hasCapacity) {
					if (beforeTime == null || checkTime.after(beforeTime)) {
						beforeTime = checkTime;
					}
				}
			}

			// Find AFTER time: check when reservations end (every 30 minutes after requested time)
			// We check capacity at time slots after the requested time
			// A reservation ends 2 hours after it starts, so we check at 2-hour intervals
			Timestamp afterTime = null;
			
			// Round requested time to next 30-minute slot
			long nextSlotMillis = ((roundedRequested / thirtyMinutesMillis) + 1) * thirtyMinutesMillis;
			Timestamp roundedRequestedNext = new Timestamp(nextSlotMillis);
			
			for (int minutesForward = 0; minutesForward <= 240; minutesForward += 30) {
				long checkTimeMillis = roundedRequestedNext.getTime() + (minutesForward * 60L * 1000L);
				Timestamp checkTime = new Timestamp(checkTimeMillis);
				
				if (!canMakeReservationAtTime(checkTime)) {
					continue;
				}
				
				boolean hasCapacity = checkCapacityAtTimeByGuests(checkTime, numberOfGuests);
				
				if (hasCapacity) {
					if (afterTime == null || checkTime.before(afterTime)) {
						afterTime = checkTime;
					}
					break;
				}
			}

			if (beforeTime != null || afterTime != null) {
				String beforeStr = beforeTime != null ? beforeTime.toString() : "";
				String afterStr = afterTime != null ? afterTime.toString() : "";
				return "ALT_TIMES:" + beforeStr + "|" + afterStr;
			}
		} catch (Exception e) {
			System.err.println("Error finding alternative times: " + e.getMessage());
			e.printStackTrace();
		}
		return "";
	}

	public static String insertReservation(Reservations reservation) {
		Timestamp requestedDateTime = Timestamp.valueOf(reservation.getOrderDateTime());
		Timestamp currentTime = new Timestamp(System.currentTimeMillis());
		
		// Check if the requested date/time is in the past
		if (requestedDateTime.before(currentTime)) {
			return "INVALID_DATE_TIME:Reservation date and time cannot be in the past.";
		}
		
		int numberOfGuests = reservation.getNumberOfGuests();
		if (numberOfGuests < 1) {
			return "INVALID_DATE_TIME:Number of guests must be at least 1.";
		}
		
		// Check if there are any tables with sufficient capacity
		int tablesWithCapacity = 0;
		try (PreparedStatement checkTablesStmt = conn.prepareStatement(
				"SELECT COUNT(*) FROM tables WHERE capacity >= ?")) {
			checkTablesStmt.setInt(1, numberOfGuests);
			try (ResultSet rs = checkTablesStmt.executeQuery()) {
				if (rs.next()) {
					tablesWithCapacity = rs.getInt(1);
				}
			}
		} catch (SQLException e) {
			System.err.println("Error checking table capacity: " + e.getMessage());
			return "Error: Could not check table availability.";
		}
		
		if (tablesWithCapacity == 0) {
			return "INVALID_DATE_TIME:No tables available with sufficient capacity for " + numberOfGuests + " guests.";
		}
		
		boolean hasCapacity = checkCapacityAtTimeByGuests(requestedDateTime, numberOfGuests);

		if (!hasCapacity) {
			String altTimes = findAlternativeTimes(requestedDateTime, numberOfGuests);
			if (!altTimes.isEmpty()) {
				return "NO_CAPACITY:" + altTimes;
			}
			return "NO_CAPACITY:NO_ALT_TIMES";
		}

	    String sql =
	        "INSERT INTO orders " +
	        "(subscriber_id, number_of_guests, confirmation_code, order_number, " +
	        "order_time_date, time_date_of_placing_order, status, is_subscriber, email, phone, name) " +
	        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
	    	
	    	// If this is a subscriber reservation, fetch subscriber's contact details
	    	String emailToUse = reservation.getEmail();
	    	String phoneToUse = reservation.getPhoneNumber();
	    	String nameToUse = reservation.getName();
	    	
	    	if (reservation.isSubscriber() && reservation.getSubscriberId() != null) {
	    		try (PreparedStatement subStmt = conn.prepareStatement(
	    				"SELECT name, email, phone FROM subscriber WHERE subscriberID = ?")) {
	    			subStmt.setInt(1, reservation.getSubscriberId());
	    			try (ResultSet rs = subStmt.executeQuery()) {
	    				if (rs.next()) {
	    					String subName = rs.getString("name");
	    					String subEmail = rs.getString("email");
	    					String subPhone = rs.getString("phone");
	    					
	    					if (subName != null) nameToUse = subName;
	    					if (subEmail != null) emailToUse = subEmail;
	    					if (subPhone != null) phoneToUse = subPhone;
	    				}
	    			}
	    		} catch (SQLException e) {
	    			System.err.println("Error fetching subscriber info: " + e.getMessage());
	    			e.printStackTrace();
	    		}
	    	}

	        if (reservation.getSubscriberId() == null) {
	            pstmt.setNull(1, java.sql.Types.INTEGER);
	        } else {
	            pstmt.setInt(1, reservation.getSubscriberId());
	        }

	        pstmt.setInt(2, reservation.getNumberOfGuests());
	        pstmt.setString(3, reservation.getConfirmationCode());
	        pstmt.setString(4, reservation.getOrderNumber());
	        pstmt.setTimestamp(5,Timestamp.valueOf(reservation.getOrderDateTime()));
	        pstmt.setTimestamp(6,Timestamp.valueOf(reservation.getPlacingOrderDate()));

	        pstmt.setString(7, reservation.getStatus());
	        pstmt.setBoolean(8, reservation.isSubscriber());
	        pstmt.setString(9, emailToUse);
	        pstmt.setString(10, phoneToUse);
	        pstmt.setString(11, nameToUse);

	        pstmt.executeUpdate();
	        return "SUCCESS";

	    } catch (SQLException e) {
	        System.err.println(" Error inserting reservation");
	        e.printStackTrace();
	        return "ERROR:" + e.getMessage();
	    }
	}
	
	/**
	 * Generate a unique confirmation code (6 characters: letters and numbers, excluding confusing characters)
	 */
	private static String generateConfirmationCode() {
		String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Excludes I, O, 0, 1
		java.util.Random random = new java.util.Random();
		
		// Try up to 20 times to generate a unique code
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder code = new StringBuilder();
			for (int i = 0; i < 6; i++) {
				code.append(chars.charAt(random.nextInt(chars.length())));
			}
			
			// Check if code exists in orders or waitingentry
			String checkSql = "SELECT COUNT(*) FROM orders WHERE confirmation_code = ? UNION ALL SELECT COUNT(*) FROM waitingentry WHERE confirmation_code = ?";
			try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
				checkStmt.setString(1, code.toString());
				checkStmt.setString(2, code.toString());
				try (ResultSet rs = checkStmt.executeQuery()) {
					int totalCount = 0;
					while (rs.next()) {
						totalCount += rs.getInt(1);
					}
					if (totalCount == 0) {
						// Code is unique
						return code.toString();
					}
				}
			} catch (SQLException e) {
				// If check fails, try next attempt
				System.err.println("Warning: Could not verify confirmation code uniqueness: " + e.getMessage());
			}
		}
		
		// Fallback: generate a code with timestamp to ensure uniqueness
		long timestamp = System.currentTimeMillis();
		String timestampCode = String.valueOf(timestamp % 1000000); // Last 6 digits
		// Pad with zeros if needed
		while (timestampCode.length() < 6) {
			timestampCode = "0" + timestampCode;
		}
		return "WT" + timestampCode; // Prefix "WT" for waiting list
	}
	
	/**
	 * Check if there's an available table right now for a given number of guests
	 * @param numberOfGuests Number of guests
	 * @return TableInfo object with tableID if available, null otherwise
	 */
	private static class TableInfo {
		int tableID;
		TableInfo(int tableID) {
			this.tableID = tableID;
		}
	}
	
	/**
	 * Check capacity based only on orders table (future reservations), not visits (active customers).
	 * Used for waiting list - we only care about future reservations that will take up tables.
	 * @param requestedDateTime The time to check capacity at
	 * @return true if there's capacity based on orders only, false otherwise
	 */
	private static boolean checkCapacityAtTimeOrdersOnly(Timestamp requestedDateTime) {
		long twoHoursMillis = 120L * 60L * 1000L; // 2 hours = 120 minutes
		Timestamp reservationEnd = new Timestamp(requestedDateTime.getTime() + twoHoursMillis);

		try {
			// Count total tables
			int totalTables = 0;
			try (PreparedStatement countTablesStmt = conn.prepareStatement("SELECT COUNT(*) FROM tables")) {
				try (ResultSet rs = countTablesStmt.executeQuery()) {
					if (rs.next()) {
						totalTables = rs.getInt(1);
					}
				}
			}

			// Count overlapping reservations ONLY (from orders table)
			// A reservation overlaps if: start < reservationEnd AND end > requestedDateTime
			// IMPORTANT: Only count future reservations (that haven't ended yet)
			// A reservation is "future" if: DATE_ADD(o.order_time_date, INTERVAL 120 MINUTE) >= NOW()
			String countReservationsSql =
					"SELECT COUNT(*) " +
					"FROM orders o " +
					"WHERE o.order_time_date < ? " +
					"AND DATE_ADD(o.order_time_date, INTERVAL 120 MINUTE) > ? " +
					"AND DATE_ADD(o.order_time_date, INTERVAL 120 MINUTE) >= NOW() " +
					"AND o.status NOT IN ('cancelled', 'Cancelled by user', 'Cancelled by resturant', 'paid')";
			int overlappingReservations = 0;
			try (PreparedStatement countResStmt = conn.prepareStatement(countReservationsSql)) {
				countResStmt.setTimestamp(1, reservationEnd);
				countResStmt.setTimestamp(2, requestedDateTime);
				try (ResultSet rs = countResStmt.executeQuery()) {
					if (rs.next()) {
						overlappingReservations = rs.getInt(1);
					}
				}
			}

			// Only count reservations, not visits
			int tablesNeeded = overlappingReservations + 1; // +1 for the new customer
			return totalTables > 0 && tablesNeeded <= totalTables;
		} catch (SQLException e) {
			System.err.println("Error during capacity check (orders only): " + e.getMessage());
	        e.printStackTrace();
	        return false;
	    }
	}
	
	/**
	 * Check if restaurant has available capacity right now, considering future reservations.
	 * Since each customer stays for 2 hours, checks if there's capacity for the entire 2-hour range
	 * from now until now + 2 hours. This ensures there's room for people who made reservations in advance.
	 * IMPORTANT: Only checks orders table (future reservations), NOT visits (active customers).
	 * If restaurant has space for the entire 2-hour range based on reservations, returns an available table. Otherwise returns null.
	 * @param numberOfGuests Number of guests
	 * @return TableInfo if restaurant has space, null if restaurant is full
	 */
	private static TableInfo findAvailableTableNow(int numberOfGuests) {
		try {
			Timestamp currentTime = new Timestamp(System.currentTimeMillis());
			
			// Get the date part and round current time down to nearest 30-minute slot
			// Example: 14:15 -> 14:00, 14:35 -> 14:30
			java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.setTime(currentTime);
			int minutes = cal.get(java.util.Calendar.MINUTE);
			int roundedMinutes = (minutes / 30) * 30; // Round down to nearest 30 minutes
			cal.set(java.util.Calendar.MINUTE, roundedMinutes);
			cal.set(java.util.Calendar.SECOND, 0);
			cal.set(java.util.Calendar.MILLISECOND, 0);
			
			// Check capacity at multiple time points in the next 2 hours
			// Since each reservation lasts 2 hours, we need to check at intervals to ensure
			// there's capacity throughout the entire 2-hour range
			// Example: if current time is 14:15 (rounded to 14:00), check: 14:00, 14:30, 15:00, 15:30, 16:00
			// This ensures there's room for people who made reservations in advance
			// IMPORTANT: Only check orders table (future reservations), NOT visits (active customers)
			boolean hasCapacityForAllTimePoints = true;
			
			for (int offsetMinutes = 0; offsetMinutes <= 120; offsetMinutes += 30) {
				java.util.Calendar checkCal = (java.util.Calendar) cal.clone();
				checkCal.add(java.util.Calendar.MINUTE, offsetMinutes);
				Timestamp checkTime = new Timestamp(checkCal.getTimeInMillis());
				
				// Check capacity at this time point based ONLY on orders (future reservations)
				boolean hasCapacity = checkCapacityAtTimeOrdersOnly(checkTime);
				
				if (!hasCapacity) {
					// Restaurant doesn't have capacity at this time point for the 2-hour range
					hasCapacityForAllTimePoints = false;
					break;
				}
			}
			
			// If restaurant doesn't have capacity at any of the checked time points, return null (customer must wait)
			if (!hasCapacityForAllTimePoints) {
				return null;
			}
			
			// Restaurant has capacity at all checked time points - find an available table right now
			String tableQuery =
				"SELECT tableID, capacity " +
				"FROM tables " +
				"WHERE tableStatus = 'AVAILABLE' AND capacity >= ? " +
				"ORDER BY capacity ASC " +
				"LIMIT 1";
			
			try (PreparedStatement tableStmt = conn.prepareStatement(tableQuery)) {
				tableStmt.setInt(1, numberOfGuests);
				try (ResultSet rs = tableStmt.executeQuery()) {
					if (rs.next()) {
						return new TableInfo(rs.getInt("tableID"));
					}
				}
			}
			
		} catch (Exception e) {
			System.err.println("Error finding available table: " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Perform immediate check-in (when table is available right now)
	 * Creates visit row and marks table as OCCUPIED
	 * @param tableInfo Table information
	 * @param numberOfGuests Number of guests
	 * @return Table ID string
	 */
	private static String performImmediateCheckIn(TableInfo tableInfo, int numberOfGuests) throws SQLException {
		return performImmediateCheckIn(tableInfo, numberOfGuests, null);
	}
	
	private static String performImmediateCheckIn(TableInfo tableInfo, int numberOfGuests, Integer subscriberId) throws SQLException {
		conn.setAutoCommit(false);
		try {
			// Mark table as OCCUPIED (no confirmationCode for immediate walk-in)
			String updateTableSql = "UPDATE tables SET tableStatus = ?, confirmationCode = NULL WHERE tableID = ?";
			try (PreparedStatement updateTable = conn.prepareStatement(updateTableSql)) {
				updateTable.setString(1, "OCCUPIED");
				updateTable.setInt(2, tableInfo.tableID);
				updateTable.executeUpdate();
			}
			
			// Insert visit row (no order_number, no confirmation_code - immediate walk-in)
			// Include subscriber_id if this is a subscriber check-in
			String insertVisitSql =
				"INSERT INTO visit (order_number, confirmation_code, tableID, startTime, subId) " +
				"VALUES (NULL, NULL, ?, NOW(), ?)";
			try (PreparedStatement insertVisit = conn.prepareStatement(insertVisitSql)) {
				insertVisit.setInt(1, tableInfo.tableID);
				// Set subscriber_id if provided
				if (subscriberId != null) {
					insertVisit.setInt(2, subscriberId);
				} else {
					insertVisit.setNull(2, java.sql.Types.INTEGER);
				}
				insertVisit.executeUpdate();
			}
			
			conn.commit();
			conn.setAutoCommit(true);
			
			System.out.println("Immediate check-in: Table " + tableInfo.tableID + " assigned to " + numberOfGuests + " guests");
			return String.valueOf(tableInfo.tableID);
			
		} catch (SQLException e) {
			conn.rollback();
			conn.setAutoCommit(true);
			throw e;
		}
	}
	
	/**
	 * Insert a waiting list entry. First checks if restaurant has available capacity in the next 2 hours.
	 * If restaurant is NOT full in the next 2 hours (has capacity), performs automatic check-in:
	 * creates an order in orders table with current time, updates status to CheckedIN, assigns a table,
	 * marks table as OCCUPIED, and creates visit record. Returns "TABLE_AVAILABLE:<tableID>".
	 * If restaurant is full, creates confirmation code, adds to waiting list, and returns "WAITING_LIST:<confirmation_code>".
	 * @param entry WaitingEntry object with guest info
	 * @return "TABLE_AVAILABLE:<tableID>" if restaurant has capacity (automatic check-in completed), "WAITING_LIST:<confirmation_code>" if restaurant is full (added to waiting list), "Error" on failure
	 */
	public static String insertWaitingEntry(WaitingEntry entry) {
	    try {
	        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
	        
	        // Check if restaurant has capacity in the next 2 hours (not full)
	        boolean hasCapacity = checkCapacityAtTimeByGuests(currentTime, entry.getNumberOfGuests());
	        
	        if (hasCapacity) {
	        	// Restaurant is NOT full in the next 2 hours - create order, perform check-in, and assign table
	        	try {
	        		// Generate confirmation code
	        		String confirmationCode = generateConfirmationCode();
	        		
	        		// Generate order number (can be same as confirmation code or different)
	        		String orderNumber = confirmationCode;
	        		
	        		// Find an available table that can accommodate the number of guests
	        		String tableQuery =
	        			"SELECT tableID, capacity " +
	        			"FROM tables " +
	        			"WHERE tableStatus = 'AVAILABLE' AND capacity >= ? " +
	        			"ORDER BY capacity ASC " +
	        			"LIMIT 1";
	        		
	        		Integer tableId = null;
	        		
	        		try (PreparedStatement tableStmt = conn.prepareStatement(tableQuery)) {
	        			tableStmt.setInt(1, entry.getNumberOfGuests());
	        			try (ResultSet rs = tableStmt.executeQuery()) {
	        				if (rs.next()) {
	        					tableId = rs.getInt("tableID");
	        				}
	        			}
	        		}
	        		
	        		if (tableId != null) {
	        			conn.setAutoCommit(false);
	        			try {
	        				String insertOrderSql =
	        					"INSERT INTO orders " +
	        					"(subscriber_id, number_of_guests, confirmation_code, order_number, " +
	        					"order_time_date, time_date_of_placing_order, status, is_subscriber, email, phone, name) " +
	        					"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	        				
	        				try (PreparedStatement orderStmt = conn.prepareStatement(insertOrderSql)) {
	        					orderStmt.setNull(1, java.sql.Types.INTEGER);
	        					orderStmt.setInt(2, entry.getNumberOfGuests());
	        					orderStmt.setString(3, confirmationCode);
	        					orderStmt.setString(4, orderNumber);
	        					orderStmt.setTimestamp(5, currentTime);
	        					orderStmt.setTimestamp(6, currentTime);
	        					orderStmt.setString(7, "PENDING");
	        					orderStmt.setBoolean(8, false);
	        					orderStmt.setString(9, entry.getEmail());
	        					orderStmt.setString(10, entry.getPhone());
	        					orderStmt.setNull(11, java.sql.Types.VARCHAR);
	        					orderStmt.executeUpdate();
	        				}
	        				
	        				String updateOrderSql = "UPDATE orders SET status = ? WHERE confirmation_code = ?";
	        				try (PreparedStatement updateOrder = conn.prepareStatement(updateOrderSql)) {
	        					updateOrder.setString(1, "CheckedIN");
	        					updateOrder.setString(2, confirmationCode);
	        					updateOrder.executeUpdate();
	        				}
	        				
	        				String updateTableSql = "UPDATE tables SET tableStatus = ?, confirmationCode = ? WHERE tableID = ?";
	        				try (PreparedStatement updateTable = conn.prepareStatement(updateTableSql)) {
	        					updateTable.setString(1, "OCCUPIED");
	        					updateTable.setString(2, confirmationCode);
	        					updateTable.setInt(3, tableId);
	        					updateTable.executeUpdate();
	        				}
	        				
	        				String insertVisitSql =
	        					"INSERT INTO visit (order_number, confirmation_code, tableID, startTime, subId) " +
	        					"VALUES (?, ?, ?, NOW(), ?)";
	        				try (PreparedStatement insertVisit = conn.prepareStatement(insertVisitSql)) {
	        					insertVisit.setString(1, orderNumber);
	        					insertVisit.setString(2, confirmationCode);
	        					insertVisit.setInt(3, tableId);
	        					insertVisit.setNull(4, java.sql.Types.INTEGER);
	        					insertVisit.executeUpdate();
	        				}
	        				
	        				conn.commit();
	        				conn.setAutoCommit(true);
	        				
	        				return "TABLE_AVAILABLE:" + tableId;
	        				
	        			} catch (SQLException e) {
	        				conn.rollback();
	        				conn.setAutoCommit(true);
	        				System.err.println("Error performing automatic check-in for waiting list entry: " + e.getMessage());
	        				e.printStackTrace();
	        				// Fall through to waiting list
	        			}
	        		}
	        	} catch (SQLException e) {
	        		System.err.println("Error processing waiting list entry with capacity: " + e.getMessage());
	        		e.printStackTrace();
	        		// If error occurs, fall through to waiting list
	        	}
	        }
	        
	        // Restaurant is full in the next 2 hours - generate confirmation code and add to waiting list (customer must wait)
	        String confirmationCode = generateConfirmationCode();
	        
	        String sql = "INSERT INTO waitingentry (number_of_guests, phone, email, date, confirmation_code, status) VALUES (?, ?, ?, ?, ?, ?)";
	        
	        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
	            pstmt.setInt(1, entry.getNumberOfGuests());
	            pstmt.setString(2, entry.getPhone());
	            pstmt.setString(3, entry.getEmail());
	            pstmt.setDate(4, java.sql.Date.valueOf(entry.getDate()));
	            pstmt.setString(5, confirmationCode);
	            pstmt.setString(6, "WAITING"); // Default status: WAITING (low priority)
	            
	            pstmt.executeUpdate();
	            return "WAITING_LIST:" + confirmationCode;
	        }
	        
	    } catch (SQLException e) {
	        System.err.println("Error inserting waiting entry: " + e.getMessage());
	        System.err.println("SQL State: " + e.getSQLState());
	        e.printStackTrace();
	        return "Error";
	    } catch (Exception e) {
	        System.err.println("General error inserting waiting entry: " + e.getMessage());
	        e.printStackTrace();
	        return "Error";
	    }
	}
	
	/**
	 * Updates a waiting list entry status to 'left' by phone or email.
	 * 
	 * @param phoneOrEmail The phone number or email to identify the entry
	 * @return "WaitingListExited" if successful, "WaitingListExitFailed" otherwise
	 */
	public static String exitWaitingList(String phoneOrEmail) {
		try {
			// Update entry status to 'left' by matching phone or email (both WAITING and P_WAITING statuses)
			String sql = "UPDATE waitingentry SET status = 'left' WHERE (phone = ? OR email = ?) AND status IN ('WAITING', 'P_WAITING' 'SEATED')";
			
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setString(1, phoneOrEmail);
				pstmt.setString(2, phoneOrEmail);
				
				int rowsAffected = pstmt.executeUpdate();
				
				if (rowsAffected > 0) {
					return "WaitingListExited";
				} else {
					return "WaitingListExitFailed: No waiting entry found with this phone or email";
				}
			}
			
		} catch (SQLException e) {
			System.err.println("Error exiting waiting list: " + e.getMessage());
			e.printStackTrace();
			return "WaitingListExitFailed: " + e.getMessage();
		}
	}
	
	/**
	 * Subscriber exit from waiting list using subscriberID.
	 * Looks up subscriber's phone/email and reuses exitWaitingList logic.
	 */
	public static String exitWaitingListForSubscriber(String subscriberIdStr) {
		if (subscriberIdStr == null) return "WaitingListExitFailed: subscriberID is null";
		
		try {
			int subscriberId = Integer.parseInt(subscriberIdStr);
			
			String email = null;
			String phone = null;
			
			String subQuery = "SELECT email, phone FROM subscriber WHERE subscriberID = ?";
			PreparedStatement subStmt = null;
			ResultSet rs = null;
			try {
				subStmt = conn.prepareStatement(subQuery);
				subStmt.setInt(1, subscriberId);
				rs = subStmt.executeQuery();
				if (rs.next()) {
					email = rs.getString("email");
					phone = rs.getString("phone");
				} else {
					return "WaitingListExitFailed: Subscriber not found";
				}
			} finally {
				if (rs != null) try { rs.close(); } catch (SQLException e) { e.printStackTrace(); }
				if (subStmt != null) try { subStmt.close(); } catch (SQLException e) { e.printStackTrace(); }
			}
			
			// Prefer phone if exists, otherwise email
			String key = (phone != null && !phone.isEmpty()) ? phone : email;
			if (key == null || key.isEmpty()) {
				return "WaitingListExitFailed: Subscriber has no phone or email";
			}
			
			return exitWaitingList(key);
			
		} catch (NumberFormatException e) {
			return "WaitingListExitFailed: invalid subscriberID";
		} catch (SQLException e) {
			e.printStackTrace();
			return "WaitingListExitFailed: " + e.getMessage();
		}
	}
	
	/**
	 * Validates if a subscriber ID exists in the subscriber table.
	 * 
	 * @param subscriberID The subscriber ID to validate
	 * @return true if subscriber exists, false otherwise
	 */
	public static boolean validateSubscriber(String subscriberID) {
	    try {
	        String sql = "SELECT subscriberID FROM subscriber WHERE subscriberID = ?";
	        
	        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
	            pstmt.setString(1, subscriberID);
	            
	            try (ResultSet rs = pstmt.executeQuery()) {
	                return rs.next(); // Returns true if subscriber exists
	            }
	        }
	    } catch (SQLException e) {
	        System.err.println("Error validating subscriber: " + e.getMessage());
	        e.printStackTrace();
	        return false;
	    }
	}

	/**
	 * Processes a payment (simulated - academic purposes only).
	 * Updates order status to "paid", adds visit record, and updates reservation.
	 * 
	 * @param payment The payment object containing payment details
	 * @return "PaymentSuccess" if successful, "PaymentFailed" otherwise
	 */
	/**
	 * Processes a payment (simulated - academic purposes only).
	 * Updates order status to "paid", adds visit record, and updates reservation.
	 * 
	 * @param payment The payment object containing payment details
	 * @return "PaymentSuccess" if successful, "PaymentFailed" otherwise
	 */
	public static String processPayment(Payment payment) {
	    try {
	        String confirmationCode = payment.getConfirmationCode();
	        Integer tableId = null;
	        
	        // First, verify the order exists
	        String orderQuery = "SELECT order_number FROM orders WHERE confirmation_code = ?";
	        try (PreparedStatement orderStmt = conn.prepareStatement(orderQuery)) {
	            orderStmt.setString(1, confirmationCode);
	            
	            try (ResultSet orderRs = orderStmt.executeQuery()) {
	                if (!orderRs.next()) {
	                    return "PaymentFailed: Order not found";
	                }
	            }
	        }
	        
	        // Get tableID from visit table (where the customer is sitting)
	        String visitQuery = "SELECT tableID FROM visit WHERE confirmation_code = ? AND endTime IS NULL";
	        try (PreparedStatement visitSelectStmt = conn.prepareStatement(visitQuery)) {
	            visitSelectStmt.setString(1, confirmationCode);
	            try (ResultSet visitRs = visitSelectStmt.executeQuery()) {
	                if (visitRs.next()) {
	                    tableId = visitRs.getObject("tableID") != null ? visitRs.getInt("tableID") : null;
	                }
	            }
	        }
	        
	        // Start transaction
	        conn.setAutoCommit(false);
	        
	        try {
	            String updateStatusQuery = "UPDATE orders SET status = ? WHERE confirmation_code = ?";
	            try (PreparedStatement updateStmt = conn.prepareStatement(updateStatusQuery)) {
	                updateStmt.setString(1, "paid");
	                updateStmt.setString(2, confirmationCode);
	                updateStmt.executeUpdate();
	            }
	            
	            String updateVisitQuery = "UPDATE visit SET endTime = NOW() WHERE confirmation_code = ? AND endTime IS NULL";
	            try (PreparedStatement visitUpdateStmt = conn.prepareStatement(updateVisitQuery)) {
	                visitUpdateStmt.setString(1, confirmationCode);
	                visitUpdateStmt.executeUpdate();
	            }
	            
	            if (tableId != null) {
	                String updateTableQuery = "UPDATE tables SET tableStatus = 'AVAILABLE', confirmationCode = NULL WHERE tableID = ?";
	                try (PreparedStatement tableUpdateStmt = conn.prepareStatement(updateTableQuery)) {
	                    tableUpdateStmt.setInt(1, tableId);
	                    tableUpdateStmt.executeUpdate();
	                }
	               
	            }
	            
	            conn.commit();
	            conn.setAutoCommit(true);
	            
	            return "PaymentSuccess";
	            
	        } catch (SQLException e) {
	            conn.rollback();
	            conn.setAutoCommit(true);
	            System.err.println("Error processing payment: " + e.getMessage());
	            e.printStackTrace();
	            return "PaymentFailed: " + e.getMessage();
	        }
	        
	    } catch (SQLException e) {
	        System.err.println("Error processing payment: " + e.getMessage());
	        e.printStackTrace();
	        return "PaymentFailed: " + e.getMessage();
	    }
	}
	
	
	/**
	 * Cancels a reservation by updating its status to "Cancelled by user".
	 * 
	 * @param confirmationCode The confirmation code of the reservation to cancel
	 * @return "Cancelled" if successful, "Error: <message>" otherwise
	 */
	public static String cancelReservation(String confirmationCode) {
		try {
			// Validate input - prevent accidental mass cancellation
			if (confirmationCode == null || confirmationCode.trim().isEmpty()) {
				System.err.println("Error: Cannot cancel reservation - confirmation code is null or empty");
				return "Error: Confirmation code is required";
			}
			
			// Trim whitespace
			confirmationCode = confirmationCode.trim();
			
			// First, verify the order exists and is not already cancelled or paid
			String checkSql = "SELECT status FROM orders WHERE confirmation_code = ?";
			try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
				checkStmt.setString(1, confirmationCode);
				try (ResultSet rs = checkStmt.executeQuery()) {
					if (!rs.next()) {
						return "Error: Reservation not found";
					}
					String currentStatus = rs.getString("status");
					if ("cancelled".equalsIgnoreCase(currentStatus) || 
					    "Cancelled by user".equalsIgnoreCase(currentStatus) ||
					    "Cancelled by resturant".equalsIgnoreCase(currentStatus)) {
						return "Error: Reservation is already cancelled";
					}
					if ("paid".equalsIgnoreCase(currentStatus)) {
						return "Error: Cannot cancel a paid reservation";
					}
				}
			}
			
			// Update the status to "Cancelled by user" - using confirmation_code (which should be unique)
			String updateSql = "UPDATE orders SET status = ? WHERE confirmation_code = ? " +
			                   "AND status NOT IN ('cancelled', 'Cancelled by user', 'Cancelled by resturant', 'paid')";
			try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
				updateStmt.setString(1, "Cancelled by user");
				updateStmt.setString(2, confirmationCode);
				int rowsAffected = updateStmt.executeUpdate();
				
				if (rowsAffected > 0) {
					System.out.println("Cancelled reservation: " + confirmationCode);
					return "Cancelled";
				} else {
					System.err.println("Warning: No rows affected when cancelling reservation: " + confirmationCode);
					return "Error: Failed to cancel reservation - reservation may have already been cancelled or paid";
				}
			}
		} catch (SQLException e) {
			System.err.println("Error cancelling reservation: " + e.getMessage());
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Get opening hours for a specific date from the opening_hours table.
	 * First checks for specific_date match, then checks for permanent hours (is_permanent=1, specific_date IS NULL).
	 * @param date The date to check (format: yyyy-MM-dd)
	 * @return Opening hours string (format: "HH:mm-HH:mm") or null if not found or default hours
	 */
	public static String getOpeningHours(String date) {
		try {
			// First, try to find specific date entry
			String sql = "SELECT opening_time, closing_time FROM opening_hours WHERE specific_date = ? AND is_active = true";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, date);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						java.sql.Time openingTime = rs.getTime("opening_time");
						java.sql.Time closingTime = rs.getTime("closing_time");
						
						if (openingTime != null && closingTime != null) {
							String openingTimeStr = openingTime.toString().substring(0, 5);
							String closingTimeStr = closingTime.toString().substring(0, 5);
							return openingTimeStr + "-" + closingTimeStr;
						}
					}
				}
			}
			
			// If no specific date entry found, check for permanent hours
			// Get the day of week from the date
			java.time.LocalDate localDate = java.time.LocalDate.parse(date);
			java.time.DayOfWeek dayOfWeekEnum = localDate.getDayOfWeek();
			String dayOfWeek = dayOfWeekEnum.toString();
			// Convert Java DayOfWeek to database format (e.g., MONDAY -> Monday)
			String dayFormatted = dayOfWeek.charAt(0) + dayOfWeek.substring(1).toLowerCase();
			
			String sqlPermanent = "SELECT opening_time, closing_time FROM opening_hours WHERE day_of_week = ? AND is_permanent = 1 AND specific_date IS NULL AND is_active = true";
			try (PreparedStatement stmt = conn.prepareStatement(sqlPermanent)) {
				stmt.setString(1, dayFormatted);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						java.sql.Time openingTime = rs.getTime("opening_time");
						java.sql.Time closingTime = rs.getTime("closing_time");
						
						if (openingTime != null && closingTime != null) {
							String openingTimeStr = openingTime.toString().substring(0, 5);
							String closingTimeStr = closingTime.toString().substring(0, 5);
							return openingTimeStr + "-" + closingTimeStr;
						}
					}
				}
			}
			
			return null;
		} catch (SQLException e) {
			System.err.println("Error getting opening hours: " + e.getMessage());
			e.printStackTrace();
			return null;
		} catch (Exception e) {
			System.err.println("Error parsing date in getOpeningHours: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Check if a given time is within the restaurant's opening hours.
	 * Default hours: 12:00 to 00:00 (midnight, meaning open until end of day)
	 * If specific hours exist in opening_hours table for that date, use those instead.
	 * @param time The timestamp to check
	 * @return true if the time is within opening hours, false otherwise
	 */
	private static boolean isWithinOpeningHours(Timestamp time) {
		try {
			java.time.LocalDate date = time.toLocalDateTime().toLocalDate();
			String dateStr = date.toString();
			
			String openingHoursStr = getOpeningHours(dateStr);
			
			java.time.LocalTime openingTime;
			java.time.LocalTime closingTime;
			
			if (openingHoursStr != null && !openingHoursStr.isEmpty()) {
				String[] parts = openingHoursStr.split("-");
				if (parts.length == 2) {
					openingTime = java.time.LocalTime.parse(parts[0]);
					closingTime = java.time.LocalTime.parse(parts[1]);
				} else {
					openingTime = java.time.LocalTime.of(12, 0);
					closingTime = java.time.LocalTime.of(0, 0);
				}
			} else {
				openingTime = java.time.LocalTime.of(12, 0);
				closingTime = java.time.LocalTime.of(0, 0);
			}
			
			java.time.LocalTime timeToCheck = time.toLocalDateTime().toLocalTime();
			
			if (closingTime.equals(java.time.LocalTime.of(0, 0))) {
				return !timeToCheck.isBefore(openingTime);
			} else {
				return !timeToCheck.isBefore(openingTime) && !timeToCheck.isAfter(closingTime);
			}
		} catch (Exception e) {
			System.err.println("Error checking opening hours: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Check if a reservation can be made at a given time.
	 * A reservation can be made if:
	 * 1. The time is within opening hours
	 * 2. The time + 2 hours (reservation duration) is still within opening hours
	 * Default: latest reservation time is 22:00 (22:00 + 2 hours = 00:00)
	 * If specific hours exist in opening_hours table, latest time is closingTime - 2 hours
	 * @param time The timestamp to check
	 * @return true if a reservation can be made at this time, false otherwise
	 */
	private static boolean canMakeReservationAtTime(Timestamp time) {
		try {
			java.time.LocalDate date = time.toLocalDateTime().toLocalDate();
			String dateStr = date.toString();
			
			String openingHoursStr = getOpeningHours(dateStr);
			
			java.time.LocalTime openingTime;
			java.time.LocalTime closingTime;
			
			if (openingHoursStr != null && !openingHoursStr.isEmpty()) {
				String[] parts = openingHoursStr.split("-");
				if (parts.length == 2) {
					openingTime = java.time.LocalTime.parse(parts[0]);
					closingTime = java.time.LocalTime.parse(parts[1]);
				} else {
					openingTime = java.time.LocalTime.of(12, 0);
					closingTime = java.time.LocalTime.of(0, 0);
				}
			} else {
				openingTime = java.time.LocalTime.of(12, 0);
				closingTime = java.time.LocalTime.of(0, 0);
			}
			
			java.time.LocalTime timeToCheck = time.toLocalDateTime().toLocalTime();
			
			java.time.LocalTime latestReservationTime;
			if (closingTime.equals(java.time.LocalTime.of(0, 0))) {
				latestReservationTime = java.time.LocalTime.of(22, 0);
			} else {
				latestReservationTime = closingTime.minusHours(2);
			}
			
			boolean isWithinOpeningHours = false;
			if (closingTime.equals(java.time.LocalTime.of(0, 0))) {
				isWithinOpeningHours = !timeToCheck.isBefore(openingTime);
			} else {
				isWithinOpeningHours = !timeToCheck.isBefore(openingTime) && !timeToCheck.isAfter(closingTime);
			}
			
			return isWithinOpeningHours && !timeToCheck.isAfter(latestReservationTime);
		} catch (Exception e) {
			System.err.println("Error checking if reservation can be made: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}







	
	/**
	 * Get reservation chart report for the previous month (default).
	 * @return List with single string containing all statistics separated by |
	 */
	public static List<String> GetReservationChartReport() {
		return GetReservationChartReport(null);
	}

}
