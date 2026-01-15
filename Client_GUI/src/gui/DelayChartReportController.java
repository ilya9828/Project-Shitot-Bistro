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
 * Controller for Delay Chart Report screen.
 * Displays automated monthly reports with pie charts for arrival timing and meal duration.
 */
public class DelayChartReportController {

    @FXML
    private Button backButton;
    @FXML
    private Label reportPeriodLabel;
    @FXML
    private Label summaryLabel;
    @FXML
    private PieChart arrivalStatusChart;
    @FXML
    private PieChart lateArrivalsChart;
    @FXML
    private PieChart mealDurationChart;
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
            // updating the selected months.
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
                    alert.setContentText("You must select at least one month to generate a report.");
                    alert.showAndWait();
                } else {
                    // Request data for selected months
                    requestDataFromServer(selectedMonths);
                }
            }
        });
    }

    /**
     * Loads the delay chart report data into the charts.
     * Called from ManagerMenuController after receiving data from server (optional).
     */
    public void loadReportData(List<String> reportData) {
        if (reportData != null && !reportData.isEmpty()) {
            updateCharts(reportData);
        } else {
            // If no data provided, request from server
            requestDataFromServer(null);
        }
    }
    
    /**
     * Requests report data from the server.
     * @param customMonths If provided, uses these months instead of previous month
     * custommonths - the months to request data from. ( kinda equals to selectedmonths!!).
     */
    private void requestDataFromServer(List<YearMonth> customMonths) {
        try {
            // System.out.println("📡 Requesting delay chart report data from server...");
            ChatClient.delayChartReportData.clear();
            // Setting the type of the expectedListType
            ChatClient.expectedListType = "delayChartReport";
            ChatClient.awaitResponse = true;
            
            HashMap<String, String> request = new HashMap<>();
            if (customMonths != null && !customMonths.isEmpty()) {
                // Build month string: "2025-01,2025-02,2024-07" format
                StringBuilder monthsStr = new StringBuilder();
                for (int i = 0; i < customMonths.size(); i++) {
                    if (i > 0) monthsStr.append(",");
                    monthsStr.append(customMonths.get(i).toString());
                }
                request.put("DelayChartReport", monthsStr.toString());
                // System.out.println("📅 Requesting custom months: " + monthsStr.toString());
            } else {
                request.put("DelayChartReport", "");
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
            
            // System.out.println("📥 Response received. Data size: " + (ChatClient.delayChartReportData != null ? ChatClient.delayChartReportData.size() : "null"));
            // if (ChatClient.delayChartReportData != null && !ChatClient.delayChartReportData.isEmpty()) {
            //     System.out.println("📊 Data content: " + ChatClient.delayChartReportData.get(0));
            // }
            
            // Load data if received
            if (ChatClient.delayChartReportData != null && !ChatClient.delayChartReportData.isEmpty()) {
                updateCharts(ChatClient.delayChartReportData);
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
     * Expected format: "reportPeriod|onTimeCount|onTimePercent|late1to14Count|late1to14Percent|late15PlusCount|late15PlusPercent|mealUnder2HrCount|mealUnder2HrPercent|mealOver2HrCount|mealOver2HrPercent|totalClients"
     */
    private void updateCharts(List<String> reportData) {
        if (reportData == null || reportData.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error Loading Report");
            alert.setHeaderText("No data available");
            alert.setContentText("The server returned no data for the selected months.\n\n" +
                                "Please check:\n" +
                                "• Server is running\n" +
                                "• Database has data for the selected period\n" +
                                "• Network connection is active\n" +
                                "• Try selecting different months");
            alert.showAndWait();
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
            int onTimeCount = Integer.parseInt(parts[1]);
            double onTimePercent = Double.parseDouble(parts[2]);
            int late1to14Count = Integer.parseInt(parts[3]);
            double late1to14Percent = Double.parseDouble(parts[4]);
            int late15PlusCount = Integer.parseInt(parts[5]);
            double late15PlusPercent = Double.parseDouble(parts[6]);
            int mealUnder2HrCount = Integer.parseInt(parts[7]);
            double mealUnder2HrPercent = Double.parseDouble(parts[8]);
            int mealOver2HrCount = Integer.parseInt(parts[9]);
            double mealOver2HrPercent = Double.parseDouble(parts[10]);
            int totalClients = Integer.parseInt(parts[11]);
            
            // System.out.println("📊 Parsed values:");
            // System.out.println("   Total Clients: " + totalClients);
            // System.out.println("   On Time: " + onTimeCount + " (" + onTimePercent + "%)");
            // System.out.println("   Late 1-14: " + late1to14Count + " (" + late1to14Percent + "%)");
            // System.out.println("   Late >15: " + late15PlusCount + " (" + late15PlusPercent + "%)");
            // System.out.println("   Meal <2hrs: " + mealUnder2HrCount + " (" + mealUnder2HrPercent + "%)");
            // System.out.println("   Meal >2hrs: " + mealOver2HrCount + " (" + mealOver2HrPercent + "%)");
            
            // Update report period label
            reportPeriodLabel.setText("Report Period: " + reportPeriod);
            
            // Set exact sizes for all charts to ensure they're identical
            // Force all charts to be exactly the same size (smaller to fit on screen)
            double chartSize = 240.0;
            
            // Disable auto-sizing and set fixed sizes - do this BEFORE setting data
            arrivalStatusChart.setPrefSize(chartSize, chartSize);
            arrivalStatusChart.setMinSize(chartSize, chartSize);
            arrivalStatusChart.setMaxSize(chartSize, chartSize);
            arrivalStatusChart.setAnimated(false); // Disable animation to prevent size changes
            arrivalStatusChart.setLegendVisible(true);
            arrivalStatusChart.setStartAngle(90); // Start from top for consistency
            
            lateArrivalsChart.setPrefSize(chartSize, chartSize);
            lateArrivalsChart.setMinSize(chartSize, chartSize);
            lateArrivalsChart.setMaxSize(chartSize, chartSize);
            lateArrivalsChart.setAnimated(false); // Disable animation to prevent size changes
            lateArrivalsChart.setLegendVisible(true);
            lateArrivalsChart.setStartAngle(90); // Start from top for consistency
            
            mealDurationChart.setPrefSize(chartSize, chartSize);
            mealDurationChart.setMinSize(chartSize, chartSize);
            mealDurationChart.setMaxSize(chartSize, chartSize);
            mealDurationChart.setAnimated(false); // Disable animation to prevent size changes
            mealDurationChart.setLegendVisible(true);
            mealDurationChart.setStartAngle(90); // Start from top for consistency
            
            // Create Arrival Status Chart (On Time vs Late)
            double totalLatePercent = late1to14Percent + late15PlusPercent;
            ObservableList<PieChart.Data> arrivalStatusData = FXCollections.observableArrayList();
            if (onTimePercent > 0) {
                arrivalStatusData.add(new PieChart.Data("On Time (" + String.format("%.1f", onTimePercent) + "%)", onTimePercent));
            }
            if (totalLatePercent > 0) {
                arrivalStatusData.add(new PieChart.Data("Late (" + String.format("%.1f", totalLatePercent) + "%)", totalLatePercent));
            }
            if (arrivalStatusData.isEmpty()) {
                // If all values are 0, add a placeholder
                arrivalStatusData.add(new PieChart.Data("No Data", 1));
            }
            arrivalStatusChart.setData(arrivalStatusData);
            arrivalStatusChart.setTitle("Arrival Status");
            // System.out.println("✅ Arrival Status Chart: " + arrivalStatusData.size() + " slices");
            
            // Create Late Arrivals Breakdown Chart (1-14 mins vs >15 mins)
            // Calculate percentages out of only late clients (excluding on-time)
            int totalLateClients = late1to14Count + late15PlusCount;
            double late1to14PercentOfLate = totalLateClients > 0 ? (late1to14Count * 100.0 / totalLateClients) : 0.0;
            double late15PlusPercentOfLate = totalLateClients > 0 ? (late15PlusCount * 100.0 / totalLateClients) : 0.0;
            
            ObservableList<PieChart.Data> lateArrivalsData = FXCollections.observableArrayList();
            if (late1to14PercentOfLate > 0) {
                lateArrivalsData.add(new PieChart.Data("Late 1-14 mins (" + String.format("%.1f", late1to14PercentOfLate) + "%)", late1to14PercentOfLate));
            }
            if (late15PlusPercentOfLate > 0) {
                lateArrivalsData.add(new PieChart.Data("Late >15 mins - Cancelled (" + String.format("%.1f", late15PlusPercentOfLate) + "%)", late15PlusPercentOfLate));
            }
            if (lateArrivalsData.isEmpty()) {
                // If all values are 0, add a placeholder
                lateArrivalsData.add(new PieChart.Data("No Data", 1));
            }
            lateArrivalsChart.setData(lateArrivalsData);
            lateArrivalsChart.setTitle("Late Arrivals Breakdown");
            // System.out.println("✅ Late Arrivals Chart: " + lateArrivalsData.size() + " slices");
            
            // Create Meal Duration Chart (<2 hours vs >2 hours)
            ObservableList<PieChart.Data> mealDurationData = FXCollections.observableArrayList();
            if (mealUnder2HrPercent > 0) {
                mealDurationData.add(new PieChart.Data("Under 2 Hours (" + String.format("%.1f", mealUnder2HrPercent) + "%)", mealUnder2HrPercent));
            }
            if (mealOver2HrPercent > 0) {
                mealDurationData.add(new PieChart.Data("Over 2 Hours (" + String.format("%.1f", mealOver2HrPercent) + "%)", mealOver2HrPercent));
            }
            if (mealDurationData.isEmpty()) {
                // If all values are 0, add a placeholder
                mealDurationData.add(new PieChart.Data("No Data", 1));
            }
            mealDurationChart.setData(mealDurationData);
            mealDurationChart.setTitle("Meal Duration");
            // System.out.println("✅ Meal Duration Chart: " + mealDurationData.size() + " slices");
            
            // Update summary label
            // Note: late1to14PercentOfLate and late15PlusPercentOfLate are already calculated above (out of only late clients)
            String summary = String.format(
                "Total Clients: %d\n" +
                "On Time: %d (%.1f%%)\n" +
                "Late: %d (%.1f%%)\n" +
                "Late 1-14 mins: %d (%.1f%%)\n" +
                "Late >15 mins: %d (%.1f%%)\n" +
                "Meals Under 2 Hours: %d (%.1f%%)\n" +
                "Meals Over 2 Hours: %d (%.1f%%)",
                totalClients,
                onTimeCount, onTimePercent,
                totalLateClients, totalLatePercent,
                late1to14Count, late1to14PercentOfLate,
                late15PlusCount, late15PlusPercentOfLate,
                mealUnder2HrCount, mealUnder2HrPercent,
                mealOver2HrCount, mealOver2HrPercent
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

}

