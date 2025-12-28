package com.kass.vocalanalysistool.view;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
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


//    public static void main(String[] args) {
//        UserSampleDatabase db = new UserSampleDatabase(false);
//
//        String ts = db.getTimeStamp();
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ss");
//        LocalDateTime dateTime = LocalDateTime.parse(ts, formatter);
//        DateTimeFormatter outDate = DateTimeFormatter.ofPattern("MM/dd");
//        String dateFormatted = dateTime.format(outDate);
//
//        System.out.println(dateFormatted);
//    }

    /**
     * Initializes the scene on startup.
     */
    public void initialize() {

        double[] f0_male = {0, 155};
        double[] f0_andro = {155, 165};
        double[] f0_female = {165, 355};

        final NumberAxis yAxis = (NumberAxis) myPitchChart.getYAxis();

        final Rectangle maleBand = createBand(yAxis, f0_male[0], f0_male[1],
                Color.rgb(80, 140, 255, 0.25));
        final Rectangle overlapBand = createBand(yAxis, f0_andro[0], f0_andro[1],
                Color.rgb(170, 120, 200, 0.35));
        final Rectangle femaleBand = createBand(yAxis, f0_female[0], f0_female[1],
                Color.rgb(255, 120, 180, 0.25));

        final Group bands = new Group(maleBand, overlapBand, femaleBand);

        // IMPORTANT: make unmanaged so StackPane won’t position it
        bands.setManaged(false);

        // Put behind the chart visuals (addFirst = behind LineChart)
        myPitchTrendStackPane.getChildren().addFirst(bands);

        Platform.runLater(() -> {
            final Node plotBg = myPitchChart.lookup(".chart-plot-background");
            if (plotBg == null) return;

            // Recompute whenever layout changes
            final Runnable relayout = () -> {
                // plotBg bounds -> scene -> stack pane local
                Bounds sceneB = plotBg.localToScene(plotBg.getBoundsInLocal());
                Bounds b = myPitchTrendStackPane.sceneToLocal(sceneB);

                // Move overlay to plot area (so it starts right of Y axis)
                bands.setTranslateX(b.getMinX());
                bands.setTranslateY(b.getMinY());

                // Clip so it can’t paint under axes
                Rectangle clip = (Rectangle) bands.getClip();
                if (clip == null) {
                    clip = new Rectangle();
                    bands.setClip(clip);
                }
                clip.setX(0);
                clip.setY(0);
                clip.setWidth(b.getWidth());
                clip.setHeight(b.getHeight());

                // Bands fill plot width only
                maleBand.setX(0);
                overlapBand.setX(0);
                femaleBand.setX(0);

                maleBand.setWidth(b.getWidth());
                overlapBand.setWidth(b.getWidth());
                femaleBand.setWidth(b.getWidth());

                // Update Y positions (axis display positions are plot-area coords)
                updateBand(yAxis, maleBand, 0, 155);
                updateBand(yAxis, overlapBand, 155, 165);
                updateBand(yAxis, femaleBand, 165, 355);
            };

            // Run once now
            relayout.run();

            // And re-run on any layout change that affects plot size/position
            myPitchChart.layoutBoundsProperty().addListener((obs, o, n) -> relayout.run());
            myPitchTrendStackPane.layoutBoundsProperty().addListener((obs, o, n) -> relayout.run());
            yAxis.lowerBoundProperty().addListener((obs, o, n) -> relayout.run());
            yAxis.upperBoundProperty().addListener((obs, o, n) -> relayout.run());
        });
    }

    /**
     * Updates the color rectangle size based on frequency range.
     *
     * @param theAxis the yaxis
     * @param theRect theRectangle object
     * @param theLowHz The low frequency
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
     * @param theAxis The yaxis
     * @param theLowHz The low frequency
     * @param theHighHz The high frequency
     * @param theColor The color of the rectangle
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


}

