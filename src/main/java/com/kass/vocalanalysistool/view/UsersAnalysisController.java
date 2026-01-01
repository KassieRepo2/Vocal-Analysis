package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.model.UserSampleDatabase;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * The users audio sample summary scene.
 *
 * @author Kassie Whitney
 * @version 12/27/2025
 */
public class UsersAnalysisController {
    /**
     * Trend chart for Pitch
     */
    @FXML
    public LineChart<String, Number> myPitchChart;

    /**
     * Trend chart for F1
     */
    @FXML
    public LineChart<String, Number> myF1Chart;

    /**
     * Trend chart for F2
     */
    @FXML
    public LineChart<String, Number> myF2Chart;

    /**
     * Trend chart for F3
     */
    @FXML
    public LineChart<String, Number> myF3Chart;

    /**
     * Trend chart for F4
     */
    @FXML
    public LineChart<String, Number> myF4Chart;

    /**
     * Pitch StackPane
     */
    @FXML
    public StackPane myPitchTrendStackPane;

    /**
     * Perceived Gender Stack Pane
     */
    @FXML
    public StackPane myGenderPerceptStackPane;

    /**
     * Perceived gender trend chart.
     */
    @FXML
    public LineChart<String, Number> myGenderPerceptChart;

    /**
     * Clears the SQL database
     */
    public Button myClearTrendButton;


    /**
     * Initializes the scene on startup.
     */
    public void initialize() {
        buildChartLayout();
        fillChart();

    }

    /**
     * Fills the charts with data.
     */
    private void fillChart() {
        //Gender perception
        final List<String[]> dailyMedians = getDailyMedian();
        final String[] latestScore = getLatestGenderScore();
        final XYChart.Series<String, Number> series = new XYChart.Series<>();

        //Pitch

        //F1

        //F2

        //F3

        //F4


        for (final String[] data : dailyMedians) {
            final String date = data[0];
            final double score = Double.parseDouble(data[1]);
            series.getData().add(new XYChart.Data<>(date, score));
        }

        series.getData().add(new XYChart.Data<>(latestScore[0],
                Double.parseDouble(latestScore[1])));
        series.setName("The Likelihood Of Your Voice Being Perceived As");

        myGenderPerceptChart.getData().add(series);
    }


    @FXML
    private void handleClearTrendButton() {
        new UserSampleDatabase(false).clearDatabase();
        myGenderPerceptChart.getData().clear();
        final XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("", -1));
        myGenderPerceptChart.getData().add(series);
        series.setName("The Likelihood Of Your Voice Being Perceived As");
    }

    /**
     * Builds the charts layout and color schemes.
     */
    private void buildChartLayout() {

        myGenderPerceptChart.setCreateSymbols(true);
        myPitchChart.setCreateSymbols(true);
        myGenderPerceptChart.setAnimated(false);
        myPitchChart.setAnimated(false);

        // Cis typical pitch frequency bands
        final double[] f0_male = {0, 155};
        final double[] f0_andro = {155, 165};
        final double[] f0_female = {165, 355};

        // Gender perception score
        final double[] femme_percept = {0.65, 1};
        final double[] andro_percept = {0.35, 0.65};
        final double[] masc_percept = {0, 0.35};

        final NumberAxis pitchYAxis = (NumberAxis) myPitchChart.getYAxis();

        final NumberAxis genderPerceptY = (NumberAxis) myGenderPerceptChart.getYAxis();

        genderPerceptY.setAutoRanging(false);
        genderPerceptY.setLowerBound(0.0);
        genderPerceptY.setUpperBound(1.0);
        genderPerceptY.setTickUnit(0.1);
        genderPerceptY.setForceZeroInRange(false);


        final Rectangle pitchMaleBand = getRectangleBand(pitchYAxis, f0_male, f0_andro,
                f0_female)[0];
        final Rectangle pitchOverlapBand = getRectangleBand(pitchYAxis, f0_male, f0_andro,
                f0_female)[1];
        final Rectangle pitchFemaleBand = getRectangleBand(pitchYAxis, f0_male, f0_andro,
                f0_female)[2];

        final Rectangle mascPerceptBand = getRectangleBand(genderPerceptY, masc_percept
                , andro_percept, femme_percept)[0];

        final Rectangle overlapPerceptBand = getRectangleBand(genderPerceptY, masc_percept,
                andro_percept,
                femme_percept)[1];

        final Rectangle femmePerceptBand = getRectangleBand(genderPerceptY, masc_percept, andro_percept,
                femme_percept)[2];

        final Group pitchBands = new Group(pitchMaleBand, pitchOverlapBand, pitchFemaleBand);

        final Group perceptBands = new Group(mascPerceptBand, overlapPerceptBand,
                femmePerceptBand);

        // IMPORTANT: make unmanaged so StackPane won’t position it
        pitchBands.setManaged(false);

        perceptBands.setManaged(false);

        // Put behind the chart visuals (addFirst = behind LineChart)
        myPitchTrendStackPane.getChildren().addFirst(pitchBands);
        myGenderPerceptStackPane.getChildren().addFirst(perceptBands);

        Platform.runLater(() -> {
            // Recompute whenever layout changes
            final Runnable relayout = () -> {

                final Node pitchPlotBg = myPitchChart.lookup(".chart-plot-background");
                final Node perceptPlotBg = myGenderPerceptChart.lookup(".chart-plot-background");
                if (pitchPlotBg == null || perceptPlotBg == null) return;

                // plotBg bounds -> scene -> stack pane local
                final Bounds pitchSceneBounds = pitchPlotBg.localToScene(pitchPlotBg.getBoundsInLocal());
                final Bounds pitchBoundary = myPitchTrendStackPane.sceneToLocal(pitchSceneBounds);

                final Bounds perceptSceneBounds =
                        perceptPlotBg.localToScene(perceptPlotBg.getBoundsInLocal());
                final Bounds perceptBoundary =
                        myGenderPerceptStackPane.sceneToLocal(perceptSceneBounds);

                // Move overlay to plot area (so it starts right of Y axis)
                pitchBands.setTranslateX(pitchBoundary.getMinX());
                pitchBands.setTranslateY(pitchBoundary.getMinY());

                perceptBands.setTranslateX(perceptBoundary.getMinX());
                perceptBands.setTranslateY(perceptBoundary.getMinY());

                // Clip so it can’t paint under axes
                Rectangle clipPitch = (Rectangle) pitchBands.getClip();
                Rectangle clipPercept = (Rectangle) perceptBands.getClip();

                if (clipPitch == null || clipPercept == null) {
                    clipPitch = new Rectangle();
                    clipPercept = new Rectangle();

                    pitchBands.setClip(clipPitch);
                    perceptBands.setClip(clipPercept);
                }

                clipPitch.setX(0);
                clipPitch.setY(0);
                clipPitch.setWidth(pitchBoundary.getWidth());
                clipPitch.setHeight(pitchBoundary.getHeight());

                clipPercept.setX(0);
                clipPercept.setY(0);
                clipPercept.setWidth(perceptBoundary.getWidth());
                clipPercept.setHeight(perceptBoundary.getHeight());

                // Bands fill plot width only
                pitchMaleBand.setX(0);
                pitchOverlapBand.setX(0);
                pitchFemaleBand.setX(0);

                mascPerceptBand.setX(0);
                overlapPerceptBand.setX(0);
                femmePerceptBand.setX(0);

                pitchMaleBand.setWidth(pitchBoundary.getWidth());
                pitchOverlapBand.setWidth(pitchBoundary.getWidth());
                pitchFemaleBand.setWidth(pitchBoundary.getWidth());

                mascPerceptBand.setWidth(perceptBoundary.getWidth());
                overlapPerceptBand.setWidth(perceptBoundary.getWidth());
                femmePerceptBand.setWidth(perceptBoundary.getWidth());

                // Update Y positions (axis display positions are plot-area coords)
                updateBand(pitchYAxis, pitchMaleBand, f0_male[0], f0_male[1]);
                updateBand(pitchYAxis, pitchOverlapBand, f0_andro[0], f0_andro[1]);
                updateBand(pitchYAxis, pitchFemaleBand, f0_female[0], f0_female[1]);

                final double genderPerceptHeight = perceptBoundary.getHeight();

                updateBand(genderPerceptY, mascPerceptBand, masc_percept[0], masc_percept[1]);
                updateBand(genderPerceptY, overlapPerceptBand, andro_percept[0],
                        andro_percept[1]);
                updateBand(genderPerceptY, femmePerceptBand, femme_percept[0], femme_percept[1]);
            };

            // Run once now
            relayout.run();

            // And re-run on any layout change that affects plot size/position
            runRelayout(pitchYAxis, relayout, myPitchChart, myPitchTrendStackPane);

            runRelayout(genderPerceptY, relayout, myGenderPerceptChart, myGenderPerceptStackPane);
        });
    }

    /**
     * Reruns the layout of the chart.
     *
     * @param theChartYAxis            The number axis object of the line chart
     * @param theRelayout              The relayout runnable.
     * @param theLineChart             The Line chart with modified background
     * @param myGenderPerceptStackPane The stack pane of the line chart that was modified
     */
    private void runRelayout(final NumberAxis theChartYAxis, final Runnable theRelayout,
                             final LineChart<String, Number> theLineChart,
                             StackPane myGenderPerceptStackPane) {

        theLineChart.layoutBoundsProperty().addListener((theObservable,
                                                         theBoundA, theBoundB) -> theRelayout.run());

        myGenderPerceptStackPane.layoutBoundsProperty().addListener((theObservable,
                                                                     theBoundA, theBoundB) -> theRelayout.run());

        theChartYAxis.lowerBoundProperty().addListener((theObservable,
                                                        theBoundA, theBoundB) -> theRelayout.run());

        theChartYAxis.upperBoundProperty().addListener((theObservable,
                                                        theBoundA, theBoundB) -> theRelayout.run());
    }

    /**
     * Renormalizes the gender color bands.
     *
     * @param rect       The color band rectangle
     * @param low        The low bound
     * @param high       The high bound
     * @param plotHeight The height of the plot
     */
    private void updateBandNormalized(final Rectangle rect,
                                      final double low, final double high,
                                      final double plotHeight) {

        // In pixel space: y=0 is top, y=plotHeight is bottom
        final double yLow = (1.0 - low) * plotHeight;
        final double yHigh = (1.0 - high) * plotHeight;

        rect.setY(Math.min(yLow, yHigh));
        rect.setHeight(Math.abs(yHigh - yLow));
    }

    /**
     * Creates an array of rectangle objects to be used as color bands for the charts.
     *
     * @param theChartYAxis    the charts y-axis object.
     * @param theFrequencyBand [male band, overlap band, female band] must be listed in this
     *                         order
     * @return an array of rectangle objects of color bands [male band, overlap band, female
     * band]
     */
    private Rectangle[] getRectangleBand(final NumberAxis theChartYAxis,
                                         final double[]... theFrequencyBand) {
        return new Rectangle[]{createBand(theChartYAxis, theFrequencyBand[0][0],
                theFrequencyBand[0][1], Color.rgb(80, 140, 255, 0.25)),
                createBand(theChartYAxis, theFrequencyBand[1][0],
                        theFrequencyBand[1][1], Color.rgb(170, 120, 200, 0.35)),
                createBand(theChartYAxis, theFrequencyBand[2][0],
                        theFrequencyBand[2][1], Color.rgb(255, 120, 180, 0.25))
        };
    }

    /**
     * Updates the color rectangle size based on frequency range.
     *
     * @param theAxis   the yaxis
     * @param theRect   theRectangle object
     * @param theLowHz  The low frequency
     * @param theHighHz The high frequency
     */
    private void updateBand(final NumberAxis theAxis, final Rectangle theRect,
                            final double theLowHz, final double theHighHz) {
        final double y1 = theAxis.getDisplayPosition(theLowHz);
        final double y2 = theAxis.getDisplayPosition(theHighHz);

        theRect.setY(Math.min(y1, y2));
        theRect.setHeight(Math.abs(y2 - y1));
    }

    /**
     * Creates the color band for the rectangle.
     *
     * @param theAxis   The yaxis
     * @param theLowHz  The low frequency
     * @param theHighHz The high frequency
     * @param theColor  The color of the rectangle
     * @return The rectangle object with the width and length based on the difference of the
     * low and high frequency.
     */
    private Rectangle createBand(final NumberAxis theAxis, final Double theLowHz,
                                 final double theHighHz, final Color theColor) {
        final Rectangle rect = new Rectangle();
        rect.setFill(theColor);
        rect.setMouseTransparent(true);

        theAxis.lowerBoundProperty().
                addListener((obs, o, n) ->
                        updateBand(theAxis, rect, theLowHz, theHighHz));
        theAxis.upperBoundProperty().addListener((obs, o, n) ->
                updateBand(theAxis, rect, theLowHz, theHighHz));
        theAxis.heightProperty().addListener((obs, o, n) ->
                updateBand(theAxis, rect, theLowHz, theHighHz));

        Platform.runLater(() -> updateBand(theAxis, rect, theLowHz, theHighHz));

        return rect;
    }


    /**
     * Gets the latest gender score to add to the chart.
     *
     * @return String array -> new String[]{date, score}
     */
    public String[] getLatestGenderScore() {
        final LocalDate localDate = LocalDate.now();

        final String date = localDate.format(DateTimeFormatter.ofPattern("MM/dd"));

        final OptionalDouble sampleScore = new UserSampleDatabase(false).getLatestScore();
        double score = Double.parseDouble("NaN");

        if (sampleScore.isPresent()) {
            score = sampleScore.getAsDouble();
            score = Math.round(score * 100) / 100.0;
        }

        return new String[]{date, String.valueOf(score)};
    }


    /**
     * Helper method to get the daily medians with formated dated as well as the scores.
     *
     * @return list of score arrays; string[0] -> date, string[1] -> score
     */
    private List<String[]> getDailyMedian() {

        final List<String[]> result = new ArrayList<>();

        final List<UserSampleDatabase.DailyMedian> lst =
                new UserSampleDatabase(false).getLast7dayMedianScore();

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyy-MM-dd");


        final DateTimeFormatter outMonth = DateTimeFormatter.ofPattern("MM");
        final DateTimeFormatter outDay = DateTimeFormatter.ofPattern("dd");

        for (UserSampleDatabase.DailyMedian item : lst) {

            final LocalDate date = item.theDate();
            final LocalDate dateFormatted = LocalDate.parse(date.toString(), formatter);
            final int theMonth = Integer.parseInt(dateFormatted.format(outMonth));
            final int theDay = Integer.parseInt(dateFormatted.format(outDay));
            final String finalDate = theMonth + "/" + theDay;
            final String score =
                    String.valueOf(Math.round(item.theMedianGenderScore() * 100) / 100.0);

            result.add(new String[]{finalDate, score});
        }

        return result;
    }

    public static void main(String[] args) {

    }
}


