package gui;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import client.ChatClient;
import client.ClientUI;
import common.UserSessionHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Controller for Reservation Chart Report screen.
 * Displays reports with pie charts for orders breakdown and waiting list status.
 */
public class ReservationChartReportController {

    @FXML
    private Button backButton;
    @FXML
    private Button exitButton;
    @FXML
    private Label reportPeriodLabel;
    @FXML
    private Label summaryLabel;
    @FXML
    private PieChart ordersChart;
    @FXML
    private PieChart waitingListChart;
    @FXML
    private Button selectMonthsButton;
    
    private List<YearMonth> selectedMonths = new ArrayList<>();

    /**
     * Initialize the controller.
     * Requests data from server if not already loaded.
     */
    @FXML
    public void initialize() {
        // Request data from server (default: previous month)
        requestDataFromServer(null);
    }
    
    /**
     * Handles the "Select Custom Months" button click.
     * Opens a dialog to select multiple months from different years.
     */
    @FXML
    private void handleSelectMonths(ActionEvent event) {
        // Create dialog
        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("Select Months for Report");
        dialog.setHeaderText("Select one or more months to include in the report\n(You can select months from different years)");
        
        // Create year and month selection
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        
        int currentYear = LocalDate.now().getYear();
        String[] monthNames = {"January", "February", "March", "April", "May", "June",
                               "July", "August", "September", "October", "November", "December"};
        
        // Create a section for each year
        VBox yearsContainer = new VBox(15);
        
        // Store all checkboxes for easy access
        java.util.Map<Integer, CheckBox[]> yearMonthCheckBoxes = new java.util.HashMap<>();
        
        for (int year = currentYear - 2; year <= currentYear + 1; year++) {
            VBox yearSection = new VBox(8);
            Label yearLabel = new Label("Year " + year + ":");
            yearLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            
            GridPane monthGrid = new GridPane();
            monthGrid.setHgap(10);
            monthGrid.setVgap(5);
            
            CheckBox[] monthCheckBoxes = new CheckBox[12];
            for (int i = 0; i < 12; i++) {
                monthCheckBoxes[i] = new CheckBox(monthNames[i]);
                monthGrid.add(monthCheckBoxes[i], i % 4, i / 4);
            }
            yearMonthCheckBoxes.put(year, monthCheckBoxes);
            
            yearSection.getChildren().addAll(yearLabel, monthGrid);
            yearsContainer.getChildren().add(yearSection);
        }
        
        // Selected months display
        Label selectedLabel = new Label("Selected: None");
        selectedLabel.setWrapText(true);
        selectedLabel.setStyle("-fx-font-weight: bold;");
        
        // Update selected label when months change
        Runnable updateSelected = () -> {
            List<YearMonth> selected = new ArrayList<>();
            for (java.util.Map.Entry<Integer, CheckBox[]> entry : yearMonthCheckBoxes.entrySet()) {
                int year = entry.getKey();
                CheckBox[] checkBoxes = entry.getValue();
                for (int i = 0; i < 12; i++) {
                    if (checkBoxes[i].isSelected()) {
                        selected.add(YearMonth.of(year, i + 1));
                    }
                }
            }
            selectedMonths = selected;
            if (selected.isEmpty()) {
                selectedLabel.setText("Selected: None");
            } else {
                StringBuilder sb = new StringBuilder("Selected: ");
                for (int i = 0; i < selected.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(selected.get(i).format(DateTimeFormatter.ofPattern("MMMM yyyy")));
                }
                selectedLabel.setText(sb.toString());
            }
        };
        
        // Add listeners to all checkboxes
        for (CheckBox[] checkBoxes : yearMonthCheckBoxes.values()) {
            for (CheckBox cb : checkBoxes) {
                cb.setOnAction(e -> updateSelected.run());
            }
        }
        
        content.getChildren().addAll(
            new Label("Select months from any year:"),
            yearsContainer,
            selectedLabel
        );
        
        scrollPane.setContent(content);
        scrollPane.setPrefViewportWidth(450);
        scrollPane.setPrefViewportHeight(400);
        
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        // Show dialog and handle result
        dialog.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                if (selectedMonths.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("No Months Selected");
                    alert.setHeaderText("Please select at least one month");
                    alert.showAndWait();
                } else {
                    // Request data for selected months
                    requestDataFromServer(selectedMonths);
                }
            }
        });
    }
    
    /**
     * Requests report data from the server.
     * @param customMonths If provided, uses these months instead of previous month
     */
    private void requestDataFromServer(List<YearMonth> customMonths) {
        try {
            // System.out.println("📡 Requesting reservation chart report data from server...");
            ChatClient.reservationChartReportData.clear();
            // Setting the type of the expectedListType
            ChatClient.expectedListType = "reservationChartReport";
            ChatClient.awaitResponse = true;
            
            HashMap<String, String> request = new HashMap<>();
            if (customMonths != null && !customMonths.isEmpty()) {
                // Build month string: "2025-01,2025-02,2024-07" format
                StringBuilder monthsStr = new StringBuilder();
                for (int i = 0; i < customMonths.size(); i++) {
                    if (i > 0) monthsStr.append(",");
                    monthsStr.append(customMonths.get(i).toString());
                }
                request.put("ReservationChartReport", monthsStr.toString());
                // System.out.println("📅 Requesting custom months: " + monthsStr.toString());
            } else {
                request.put("ReservationChartReport", "");
            }
            ClientUI.chat.accept(request);
            
            // System.out.println("⏳ Waiting for server response...");
            // Wait for server response (with timeout)
            int maxWait = 50; // 5 seconds max wait (50 * 100ms)
            int waited = 0;
            while (ChatClient.awaitResponse && waited < maxWait) {
                Thread.sleep(100);
                waited++;
            }
            
            // System.out.println("📥 Response received. Data size: " + (ChatClient.reservationChartReportData != null ? ChatClient.reservationChartReportData.size() : "null"));
            // if (ChatClient.reservationChartReportData != null && !ChatClient.reservationChartReportData.isEmpty()) {
            //     System.out.println("📊 Data content: " + ChatClient.reservationChartReportData.get(0));
            // }
            
            // Load data if received
            if (ChatClient.reservationChartReportData != null && !ChatClient.reservationChartReportData.isEmpty()) {
                updateCharts(ChatClient.reservationChartReportData);
            } else {
                reportPeriodLabel.setText("No data available for the previous month");
                summaryLabel.setText("No report data found. Please check:\n1. Server is running\n2. Database has data for previous month\n3. Network connection is active\n\nCheck server console for debug output.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error requesting report data: " + e.getMessage());
            e.printStackTrace();
            reportPeriodLabel.setText("Error loading report");
            summaryLabel.setText("Failed to load report data. Please try again.\nError: " + e.getMessage());
        }
    }

    /**
     * Updates the charts with the report data.
     * Expected format: "reportPeriod|reservationsCount|reservationsPercent|waitingListOrdersCount|waitingListOrdersPercent|totalWaitingList|checkedInCount|checkedInPercent|leftFromWaitingCount|leftFromWaitingPercent|totalOrders|totalWaitingListOutcomes"
     */
    private void updateCharts(List<String> reportData) {
        if (reportData == null || reportData.isEmpty()) {
            // System.err.println("❌ updateCharts: reportData is null or empty");
            return;
        }
        
        // Parse the report data (should be a single string with all statistics)
        String data = reportData.get(0);
        // System.out.println("📊 Parsing report data: " + data);
        String[] parts = data.split("\\|");
        
        // System.out.println("📊 Split into " + parts.length + " parts");
        if (parts.length < 12) {
            // System.err.println("❌ Invalid report data format. Expected 12 parts, got " + parts.length);
            // System.err.println("   Data: " + data);
            // for (int i = 0; i < parts.length; i++) {
            //     System.err.println("   Part[" + i + "]: " + parts[i]);
            // }
            return;
        }
        
        try {
            String reportPeriod = parts[0];
            int successfulVisitsCount = Integer.parseInt(parts[1]);
            double successfulVisitsPercent = Double.parseDouble(parts[2]);
            int unsuccessfulVisitsCount = Integer.parseInt(parts[3]);
            double unsuccessfulVisitsPercent = Double.parseDouble(parts[4]);
            int totalWaitingList = Integer.parseInt(parts[5]);
            int checkedInCount = Integer.parseInt(parts[6]);
            double checkedInPercent = Double.parseDouble(parts[7]);
            int leftFromWaitingCount = Integer.parseInt(parts[8]);
            double leftFromWaitingPercent = Double.parseDouble(parts[9]);
            int totalVisits = Integer.parseInt(parts[10]);
            int totalWaitingListOutcomes = Integer.parseInt(parts[11]);
            
            // System.out.println("📊 Parsed values:");
            // System.out.println("   Total Orders Made: " + totalOrders);
            // System.out.println("   From Reservations: " + reservationsCount + " (" + reservationsPercent + "%)");
            // System.out.println("   From Waiting List: " + waitingListOrdersCount + " (" + waitingListOrdersPercent + "%)");
            // System.out.println("   Total Waiting List Entries: " + totalWaitingList);
            // System.out.println("   Waiting List Outcomes: " + totalWaitingListOutcomes);
            // System.out.println("   Checked In: " + checkedInCount + " (" + checkedInPercent + "%)");
            // System.out.println("   Left From Waiting: " + leftFromWaitingCount + " (" + leftFromWaitingPercent + "%)");
            
            // Update report period label
            reportPeriodLabel.setText("Report Period: " + reportPeriod);
            
            // Set exact sizes for all charts to ensure they're identical
            double chartSize = 240.0;
            
            ordersChart.setPrefSize(chartSize, chartSize);
            ordersChart.setMinSize(chartSize, chartSize);
            ordersChart.setMaxSize(chartSize, chartSize);
            ordersChart.setAnimated(false);
            ordersChart.setLegendVisible(true);
            ordersChart.setStartAngle(90);
            
            waitingListChart.setPrefSize(chartSize, chartSize);
            waitingListChart.setMinSize(chartSize, chartSize);
            waitingListChart.setMaxSize(chartSize, chartSize);
            waitingListChart.setAnimated(false);
            waitingListChart.setLegendVisible(true);
            waitingListChart.setStartAngle(90);
            
            // Create Visits Breakdown Chart (Total Visits)
            // Shows: Successful Visits (Paid) vs Unsuccessful Visits (Not Paid)
            ObservableList<PieChart.Data> ordersData = FXCollections.observableArrayList();
            if (successfulVisitsPercent > 0) {
                ordersData.add(new PieChart.Data("Successful Visits (" + String.format("%.1f", successfulVisitsPercent) + "%)", successfulVisitsPercent));
            }
            if (unsuccessfulVisitsPercent > 0) {
                ordersData.add(new PieChart.Data("Unsuccessful Visits (" + String.format("%.1f", unsuccessfulVisitsPercent) + "%)", unsuccessfulVisitsPercent));
            }
            if (ordersData.isEmpty()) {
                ordersData.add(new PieChart.Data("No Data", 1));
            }
            ordersChart.setData(ordersData);
            ordersChart.setTitle("Total Visits");
            // System.out.println("✅ Orders Chart: " + ordersData.size() + " slices");
            
            // Create Waiting List Outcomes Chart (Lost Orders from Waiting List)
            // Shows: Checked In vs Left (from waiting list only)
            ObservableList<PieChart.Data> waitingListData = FXCollections.observableArrayList();
            if (checkedInPercent > 0) {
                waitingListData.add(new PieChart.Data("Checked In (" + String.format("%.1f", checkedInPercent) + "%)", checkedInPercent));
            }
            if (leftFromWaitingPercent > 0) {
                waitingListData.add(new PieChart.Data("Left (" + String.format("%.1f", leftFromWaitingPercent) + "%)", leftFromWaitingPercent));
            }
            if (waitingListData.isEmpty()) {
                waitingListData.add(new PieChart.Data("No Data", 1));
            }
            waitingListChart.setData(waitingListData);
            waitingListChart.setTitle("Waiting List Outcomes");
            // System.out.println("✅ Waiting List Chart: " + waitingListData.size() + " slices");
            
            // Update summary label
            String summary = String.format(
                "Total Visits: %d\n" +
                "Successful Visits: %d (%.1f%%)\n" +
                "Unsuccessful Visits: %d (%.1f%%)\n" +
                "\nTotal Waiting List Entries: %d\n" +
                "Checked In: %d (%.1f%%)\n" +
                "Left: %d (%.1f%%)",
                totalVisits,
                successfulVisitsCount, successfulVisitsPercent,
                unsuccessfulVisitsCount, unsuccessfulVisitsPercent,
                totalWaitingList,
                checkedInCount, checkedInPercent,
                leftFromWaitingCount, leftFromWaitingPercent
            );
            summaryLabel.setText(summary);
            
        } catch (NumberFormatException e) {
            System.err.println("Error parsing report data: " + e.getMessage());
            e.printStackTrace();
            summaryLabel.setText("Error parsing report data.");
        }
    }

    /**
     * This method is for the back button closing the current GUI and uploading the menu GUI.
     * @param event - click on the back button.
     * @throws IOException
     */
    @FXML
    public void Back(ActionEvent event) throws IOException {
        UserSessionHelper.navigateBackToMenu((Node) event.getSource());
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

