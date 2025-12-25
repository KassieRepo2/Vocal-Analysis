package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.model.Recorder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class AudioRecordingController implements PropertyChangeListener {
    /**
     * Analyzes the users new recording
     */
    @FXML
    private Button myAnalyzeButton;

    /**
     * The hour label
     */
    @FXML
    private Label myHourLabel;

    /**
     * The minute label
     */
    @FXML
    private Label myMinuteLabel;

    /**
     * The second label
     */
    @FXML
    private Label mySecondsLabel;

    /**
     * The audio recording button
     */
    @FXML
    private Button myRecordBtn;

    /**
     * Stops the audio recording
     */
    @FXML
    private Button myStopBtn;

    /**
     * Exits the scene
     */
    @FXML
    private Button myExitBtn;

    /**
     * This scenes property change component
     */
    private final PropertyChangeSupport myChanges = new PropertyChangeSupport(this);

    /**
     * The recorder model class. Starts the timer and the recording session.
     */
    private final static Recorder MY_RECORDER = new Recorder();

    /**
     * Initializes the recording time
     */
    @FXML
    public void init() {
        myHourLabel.setText("00");
        myMinuteLabel.setText("00");
        mySecondsLabel.setText("00");
        myAnalyzeButton.setDisable(true);
        MY_RECORDER.addPropertyChangeListener(this);
    }

    @FXML
    private void handleRecordBtn(){
        myRecordBtn.setDisable(true);
        myStopBtn.setDisable(false);

    }

    @FXML
    private void handleStopBtn() {

    }

    @FXML
    private void handleExitBtn() {

    }

    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {

    }
}
