package Server;
import java.sql.Timestamp;
import java.time.LocalDate;
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
				System.err.println("Warning: Failed to generate monthly reports: " + e.getMessage());
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
			return;
		}
		
		try {
			// Find the earliest order date to determine how far back to check
			java.time.YearMonth earliestMonth = findEarliestMonthWithOrders();
			if (earliestMonth == null) {
				System.out.println("No orders found in database, skipping report generation");
				return;
			}
			
			// Calculate the previous month (last month that should have reports)
			java.time.LocalDate now = java.time.LocalDate.now();
			java.time.LocalDate firstDayOfCurrentMonth = now.withDayOfMonth(1);
			java.time.LocalDate lastDayOfPreviousMonth = firstDayOfCurrentMonth.minusDays(1);
			java.time.LocalDate firstDayOfPreviousMonth = lastDayOfPreviousMonth.withDayOfMonth(1);
			java.time.YearMonth previousMonth = java.time.YearMonth.from(firstDayOfPreviousMonth);
			
			// Check each month from earliest to previous month
			java.time.YearMonth currentCheck = earliestMonth;
			
			while (!currentCheck.isAfter(previousMonth)) {
				List<java.time.YearMonth> months = new ArrayList<>();
				months.add(currentCheck);
				String monthsJson = monthsToJson(months);
				
				// Check if delay report exists
				String delayReport = findSavedReport("delay", monthsJson);
				if (delayReport == null) {
					generateReportForMonth(currentCheck, "delay");
				}
				
				// Check if reservation report exists
				String reservationReport = findSavedReport("reservation", monthsJson);
				if (reservationReport == null) {
					generateReportForMonth(currentCheck, "reservation");
				}
				
				// Move to next month
				currentCheck = currentCheck.plusMonths(1);
			}
			
		} catch (Exception e) {
			System.err.println("Couldnt generate monthly reports: " + e.getMessage());
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
			// Silent failure
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
			// Silent failure
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
				System.out.println("Scheduled monthly report check running on " + 
					java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
				generateMonthlyReportsIfNeeded();
				
				// After running, schedule the next check for the 1st of the following month
				scheduleNextMonthlyCheck();
			} catch (Exception e) {
				// Still schedule the next check even if this one failed
				scheduleNextMonthlyCheck();
			}
		}, delayMinutes, TimeUnit.MINUTES);
	}
	
	/**
	 * Stops the monthly report scheduler. Should be called when shutting down the server.
	 */
	public static void stopMonthlyReportScheduler() {
		if (reportScheduler != null && !reportScheduler.isShutdown()) {
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
			e.printStackTrace();
			
			// Check if error is due to duplicate entry
			if (e.getSQLState() != null && e.getSQLState().equals("23000")) { // Integrity constraint violation
				return "SubscriberExists";
			}
			// Return more detailed error for debugging
			return "Error: " + e.getMessage();
		} catch (Exception e) {
			e.printStackTrace();
			return "Error: " + e.getMessage();
		}
	}

	/**
	 * Check if a reservation time is within the given opening hours
	 * @param reservationTime The reservation time
	 * @param openingTimeStr Opening time in HH:mm format
	 * @param closingTimeStr Closing time in HH:mm format
	 * @return true if the reservation time is within opening hours, false otherwise
	 */
	private static boolean isReservationTimeWithinHours(java.time.LocalDateTime reservationTime, String openingTimeStr, String closingTimeStr) {
		try {
			java.time.LocalTime reservationLocalTime = reservationTime.toLocalTime();
			java.time.LocalTime openingTime = java.time.LocalTime.parse(openingTimeStr);
			java.time.LocalTime closingTime = java.time.LocalTime.parse(closingTimeStr);
			
			// Handle case where closing time is 00:00 (midnight, meaning open until end of day)
			if (closingTime.equals(java.time.LocalTime.of(0, 0))) {
				return !reservationLocalTime.isBefore(openingTime);
			} else {
				return !reservationLocalTime.isBefore(openingTime) && !reservationLocalTime.isAfter(closingTime);
			}
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Get reservations for a specific date
	 * @param date The date to check (format: yyyy-MM-dd)
	 * @return List of reservations with name, order_time_date, confirmation_code, email, and phone
	 */
	private static List<java.util.Map<String, Object>> getReservationsForDate(String date) {
		List<java.util.Map<String, Object>> reservations = new ArrayList<>();
		
		if (conn == null) {
			return reservations;
		}
		
		try {
			String sql = "SELECT name, order_time_date, confirmation_code, email, phone FROM orders WHERE DATE(order_time_date) = ? AND status = 'PENDING'";
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setString(1, date);
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						java.util.Map<String, Object> reservation = new java.util.HashMap<>();
						reservation.put("name", rs.getString("name"));
						reservation.put("order_time_date", rs.getTimestamp("order_time_date"));
						reservation.put("confirmation_code", rs.getString("confirmation_code"));
						reservation.put("email", rs.getString("email"));
						reservation.put("phone", rs.getString("phone"));
						reservations.add(reservation);
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return reservations;
	}
	
	/**
	 * Check and notify about conflicting reservations when opening hours change
	 * Also updates the status of conflicting reservations to "Cancelled by resturant"
	 * @param dateStr The date to check (format: yyyy-MM-dd)
	 * @param oldOpeningTime Old opening time in HH:mm format (can be null)
	 * @param oldClosingTime Old closing time in HH:mm format (can be null)
	 * @param newOpeningTime New opening time in HH:mm format
	 * @param newClosingTime New closing time in HH:mm format
	 */
	private static void checkConflictingReservations(String dateStr, String oldOpeningTime, String oldClosingTime, String newOpeningTime, String newClosingTime) {
		// If old hours are null, use default (12:00-22:00)
		if (oldOpeningTime == null || oldClosingTime == null) {
			oldOpeningTime = "12:00";
			oldClosingTime = "22:00";
		}
		
		// Get all reservations for this date
		List<java.util.Map<String, Object>> reservations = getReservationsForDate(dateStr);
		
		if (reservations.isEmpty()) {
			return; // No reservations to check
		}
		
		for (java.util.Map<String, Object> reservation : reservations) {
			Timestamp orderTime = (Timestamp) reservation.get("order_time_date");
			String confirmationCode = (String) reservation.get("confirmation_code");
			String email = (String) reservation.get("email");
			String phone = (String) reservation.get("phone");
			
			if (orderTime != null) {
				java.time.LocalDateTime reservationDateTime = orderTime.toLocalDateTime();
				
				// Check if reservation was valid under old hours but invalid under new hours
				boolean validUnderOldHours = isReservationTimeWithinHours(reservationDateTime, oldOpeningTime, oldClosingTime);
				boolean validUnderNewHours = isReservationTimeWithinHours(reservationDateTime, newOpeningTime, newClosingTime);
				
				if (validUnderOldHours && !validUnderNewHours) {
					// Reservation conflicts with new hours
					String message = "we are sorry the restaurant will be closed by the reserved times";
					
					// Send notification to customer
					if (confirmationCode != null && !confirmationCode.isEmpty()) {
						// Send SMS if phone is available
						if (phone != null && !phone.trim().isEmpty()) {
							System.out.println("[Opening Hours Change] SMS was sent to customer:(Code: " + confirmationCode + ") - " + message);
						}
						
						// Send Email if email is available
						if (email != null && !email.trim().isEmpty()) {
							System.out.println("[Opening Hours Change] email was sent to customer:(Code: " + confirmationCode + ") - " + message);
						}
					}
					
					// Update status to "Cancelled by resturant"
					if (confirmationCode != null && !confirmationCode.isEmpty()) {
						try {
							String updateSql = "UPDATE orders SET status = 'Cancelled by resturant' WHERE confirmation_code = ?";
							try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
								updateStmt.setString(1, confirmationCode);
								updateStmt.executeUpdate();
							}
						} catch (SQLException e) {
							System.err.println("Error updating reservation status for " + confirmationCode + ": " + e.getMessage());
							e.printStackTrace();
						}
					}
				}
			}
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
				
				// Get old opening hours before updating
				String oldHoursStr = getOpeningHours(dateStr);
				String oldOpeningTime = null;
				String oldClosingTime = null;
				if (oldHoursStr != null && !oldHoursStr.isEmpty()) {
					String[] oldHours = oldHoursStr.split("-");
					if (oldHours.length == 2) {
						oldOpeningTime = oldHours[0].trim();
						oldClosingTime = oldHours[1].trim();
					}
				}
				
				// Check for conflicting reservations before updating
				checkConflictingReservations(dateStr, oldOpeningTime, oldClosingTime, openingTime, closingTime);
				
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
					
					String dateStr = currentDate.toString();
					
					// Get old opening hours before updating
					String oldHoursStr = getOpeningHours(dateStr);
					String oldOpeningTime = null;
					String oldClosingTime = null;
					if (oldHoursStr != null && !oldHoursStr.isEmpty()) {
						String[] oldHours = oldHoursStr.split("-");
						if (oldHours.length == 2) {
							oldOpeningTime = oldHours[0].trim();
							oldClosingTime = oldHours[1].trim();
						}
					}
					
					// Check for conflicting reservations before updating
					checkConflictingReservations(dateStr, oldOpeningTime, oldClosingTime, openingTime, closingTime);
					
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
						// Get old permanent opening hours for this day before updating
						String oldOpeningTime = null;
						String oldClosingTime = null;
						try {
							String sqlOld = "SELECT opening_time, closing_time FROM opening_hours WHERE day_of_week = ? AND is_permanent = 1 AND specific_date IS NULL AND is_active = true LIMIT 1";
							try (PreparedStatement pstmtOld = conn.prepareStatement(sqlOld)) {
								pstmtOld.setString(1, day);
								try (ResultSet rsOld = pstmtOld.executeQuery()) {
									if (rsOld.next()) {
										java.sql.Time oldOpening = rsOld.getTime("opening_time");
										java.sql.Time oldClosing = rsOld.getTime("closing_time");
										if (oldOpening != null && oldClosing != null) {
											oldOpeningTime = oldOpening.toString().substring(0, 5);
											oldClosingTime = oldClosing.toString().substring(0, 5);
										}
									}
								}
							}
						} catch (SQLException e) {
							// If error getting old hours, continue with null (will use defaults)
						}
						
						// Check all future reservations for this day of week
						// Get all future PENDING reservations and group by date to avoid duplicate checks
						java.util.Set<String> checkedDates = new java.util.HashSet<>();
						try {
							String sqlReservations = "SELECT name, order_time_date FROM orders WHERE order_time_date >= NOW() AND status = 'PENDING' ORDER BY order_time_date";
							try (PreparedStatement pstmtRes = conn.prepareStatement(sqlReservations);
							     ResultSet rsRes = pstmtRes.executeQuery()) {
								while (rsRes.next()) {
									Timestamp orderTime = rsRes.getTimestamp("order_time_date");
									if (orderTime != null) {
										java.time.LocalDateTime reservationDateTime = orderTime.toLocalDateTime();
										java.time.DayOfWeek reservationDayOfWeek = reservationDateTime.getDayOfWeek();
										String reservationDayFormatted = reservationDayOfWeek.toString().charAt(0) + reservationDayOfWeek.toString().substring(1).toLowerCase();
										
										// Check if this reservation is for the day we're updating
										if (reservationDayFormatted.equals(day)) {
											String dateStr = reservationDateTime.toLocalDate().toString();
											
											// Only check each date once
											if (!checkedDates.contains(dateStr)) {
												checkedDates.add(dateStr);
												
												// Get specific date hours if they exist (they override permanent)
												String specificHoursStr = getOpeningHours(dateStr);
												String dateOldOpeningTime = oldOpeningTime;
												String dateOldClosingTime = oldClosingTime;
												
												if (specificHoursStr != null && !specificHoursStr.isEmpty()) {
													String[] specificHours = specificHoursStr.split("-");
													if (specificHours.length == 2) {
														dateOldOpeningTime = specificHours[0].trim();
														dateOldClosingTime = specificHours[1].trim();
													}
												}
												
												// Check if reservation conflicts
												checkConflictingReservations(dateStr, dateOldOpeningTime, dateOldClosingTime, openingTime, closingTime);
											}
										}
									}
								}
							}
						} catch (SQLException e) {
						}
						
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
			e.printStackTrace();
			return "Error: " + e.getMessage();
		} catch (Exception e) {
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
			e.printStackTrace();
			return "Error: " + e.getMessage();
		} catch (Exception e) {
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
			return waitingList;
		}

		try {
			String sql = "SELECT waitingID, number_of_guests, phone, date, status, created_at FROM waitingentry WHERE status IN ('WAITING', 'P_WAITING') ORDER BY waitingID";
			
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
		}
		return null;
	}
	
	/**
	 * Helper: save a generated report into report_history
	 * This method is called after generating a report, so it should always save (unless there's a duplicate key)
	 */
	private static void saveReport(String reportType, String monthsJson, String periodLabel, String payload, java.sql.Timestamp createdAt) {
		if (conn == null) {
			return;
		}
		
		// First, check if report already exists to prevent duplicates
		String existingReport = findSavedReport(reportType, monthsJson);
		if (existingReport != null) {
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
			pstmt.executeUpdate();
		} catch (SQLException e) {
			// Silent failure
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
				reportData.add(cached);
				return reportData;
			}
			
			// Build report period string
			StringBuilder reportPeriod = new StringBuilder();
			for (int i = 0; i < months.size(); i++) {
				if (i > 0) reportPeriod.append(" + ");
				reportPeriod.append(months.get(i).getMonth().toString()).append(" ").append(months.get(i).getYear());
			}
			
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
				}
				
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
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
			
			
			// saving the report for future retrieval
			try {
				java.sql.Timestamp createdAt = firstMonthStart(months);
				saveReport("delay", monthsToJson(months), reportPeriod.toString(), reportString, createdAt);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			reportData.add(reportString);
			
		} catch (SQLException e) {
			e.printStackTrace();
			String errorReport = "ERROR|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(errorReport);
		} catch (Exception e) {
			e.printStackTrace();
			String errorReport = "ERROR|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(errorReport);
		}
		
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
				
				
				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
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
			
			
			reportData.add(reportString);
			
		} catch (SQLException e) {
			e.printStackTrace();
			// Return empty report with error indicator
			String errorReport = "ERROR|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(errorReport);
		} catch (Exception e) {
			e.printStackTrace();
			// Return empty report with error indicator
			String errorReport = "ERROR|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0|0.00|0";
			reportData.add(errorReport);
		}
		
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
				reportData.add(cached);
				return reportData;
			}
			
			// Build report period string
			StringBuilder reportPeriod = new StringBuilder();
			for (int i = 0; i < months.size(); i++) {
				if (i > 0) reportPeriod.append(" + ");
				reportPeriod.append(months.get(i).getMonth().toString()).append(" ").append(months.get(i).getYear());
			}
			
			
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
			
			
			// saving the report for future retrieval
			try {
				java.sql.Timestamp createdAt = firstMonthStart(months);
				saveReport("reservation", monthsToJson(months), reportPeriod.toString(), reportString, createdAt);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			reportData.add(reportString);
			
		} catch (SQLException e) {
			e.printStackTrace();
			String errorReport = "ERROR|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0";
			reportData.add(errorReport);
		} catch (Exception e) {
			e.printStackTrace();
			String errorReport = "ERROR|0|0.00|0|0.00|0|0|0.00|0|0.00|0|0";
			reportData.add(errorReport);
		}
		
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
			e.printStackTrace();
		} catch (Exception e) {
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
					e.printStackTrace();
				} catch (Exception e) {
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
	/**
	 * Remove waiting entries that are older than 15 minutes
	 * Also removes SEATED entries that don't have a corresponding visit record
	 */
	private static void removeExpiredWaitingEntries() {
		try {
			// Remove WAITING entries older than 15 minutes
			String deleteWaitingSql = 
				"DELETE FROM waitingentry " +
				"WHERE created_at <= DATE_SUB(NOW(), INTERVAL 15 MINUTE) " +
				"AND status IN ('WAITING')";
			
			int waitingDeleted = 0;
			try (PreparedStatement deleteStmt = conn.prepareStatement(deleteWaitingSql)) {
				waitingDeleted = deleteStmt.executeUpdate();
			}
			
			// Remove SEATED entries that don't have a corresponding visit record
			String deleteSeatedSql = 
				"DELETE w FROM waitingentry w " +
				"LEFT JOIN visit v ON w.confirmation_code = v.confirmation_code " +
				"WHERE w.status = 'SEATED' " +
				"AND v.confirmation_code IS NULL";
			
			int seatedDeleted = 0;
			try (PreparedStatement deleteSeatedStmt = conn.prepareStatement(deleteSeatedSql)) {
				seatedDeleted = deleteSeatedStmt.executeUpdate();
			}
			
			if (waitingDeleted > 0 || seatedDeleted > 0) {
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void processWaitingList() {
		try {
			// First, remove expired waiting entries (older than 15 minutes)
			removeExpiredWaitingEntries();
			
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
								e.printStackTrace();
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (Exception e) {
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
					e.printStackTrace();
				} catch (Exception e) {
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
			e.printStackTrace();
		} catch (Exception e) {
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
					e.printStackTrace();
				} catch (Exception e) {
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
			e.printStackTrace();
		} catch (Exception e) {
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
					e.printStackTrace();
				} catch (Exception e) {
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
				e.printStackTrace();
				return "CheckInFailed:ServerError";
			}

		} catch (SQLException e) {
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
							e.printStackTrace();
							// Fall through to waiting list
						}
					}
				} catch (SQLException e) {
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
			return "Error";
		} catch (SQLException e) {
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
		} catch (SQLException e) {
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
			return "Error";
		} catch (SQLException e) {
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
			return false;
		} catch (SQLException e) {
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
			
			
			// Simulate table assignment using greedy algorithm:
			// Assign each reservation (including the new one) to the smallest table that can accommodate it
			// Sort reservations by number of guests (descending) to assign larger groups first
			List<Integer> reservationsToAssign = new ArrayList<>(overlappingReservations);
			reservationsToAssign.add(numberOfGuests); // Add the new reservation
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
			
			// All reservations (including the new one) can be assigned
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
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
	        e.printStackTrace();
	        return "Error";
	    } catch (Exception e) {
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
	            e.printStackTrace();
	            return "PaymentFailed: " + e.getMessage();
	        }
	        
	    } catch (SQLException e) {
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
					return "Error: Failed to cancel reservation - reservation may have already been cancelled or paid";
				}
			}
		} catch (SQLException e) {
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
			e.printStackTrace();
			return null;
		} catch (Exception e) {
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
			// Check if the time is within opening hours
			if (!isWithinOpeningHours(time)) {
				return false;
			}
			
			// Check if time + 2 hours is still within opening hours
			long twoHoursMillis = 120L * 60L * 1000L; // 2 hours = 120 minutes
			Timestamp reservationEnd = new Timestamp(time.getTime() + twoHoursMillis);
			if (!isWithinOpeningHours(reservationEnd)) {
				return false;
			}
			
			// Additional check: ensure reservation doesn't start too late
			// Latest reservation time should be closingTime - 2 hours
			java.time.LocalDate date = time.toLocalDateTime().toLocalDate();
			String dateStr = date.toString();
			String openingHoursStr = getOpeningHours(dateStr);
			
			java.time.LocalTime closingTime;
			if (openingHoursStr != null && !openingHoursStr.isEmpty()) {
				String[] parts = openingHoursStr.split("-");
				if (parts.length == 2) {
					closingTime = java.time.LocalTime.parse(parts[1]);
				} else {
					closingTime = java.time.LocalTime.of(0, 0);
				}
			} else {
				closingTime = java.time.LocalTime.of(0, 0);
			}
			
			java.time.LocalTime timeToCheck = time.toLocalDateTime().toLocalTime();
			java.time.LocalTime latestReservationTime;
			if (closingTime.equals(java.time.LocalTime.of(0, 0))) {
				latestReservationTime = java.time.LocalTime.of(22, 0);
			} else {
				latestReservationTime = closingTime.minusHours(2);
			}
			return !timeToCheck.isAfter(latestReservationTime);
		} catch (Exception e) {
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
