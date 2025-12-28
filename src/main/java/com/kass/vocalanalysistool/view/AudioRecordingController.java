package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.common.ChangeEvents;
import com.kass.vocalanalysistool.model.Recorder;
import com.kass.vocalanalysistool.workflow.OpenAudioDataScene;
import com.kass.vocalanalysistool.workflow.PythonRunnerService;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class AudioRecordingController implements PropertyChangeListener {

    /**
     * The close button.
     */
    @FXML
    private Button myCloseBtn;

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
     * This scenes property change component
     */
    private final PropertyChangeSupport myChanges = new PropertyChangeSupport(this);

    /**
     * The recorder model class. Starts the timer and the recording session.
     */
    private final static Recorder MY_RECORDER = new Recorder();

    /**
     * The python Runner Service
     */
    private final static PythonRunnerService MY_RUNNER_SERVICE = new PythonRunnerService();

    private Stage myStage;


    /**
     * Automatically initializes the recording time, as well as the property change listeners
     */
    @FXML
    public void initialize() {
        reset();
        myAnalyzeButton.setDisable(true);
        MY_RECORDER.addPropertyChangeListener(this);
        this.addPropertyChangeListener(MY_RECORDER);
        MY_RUNNER_SERVICE.addPropertyChangeListener(this);

    }

    /**
     * Handles the record button.
     */
    @FXML
    private void handleRecordBtn() {
        myRecordBtn.setDisable(true);
        myStopBtn.setDisable(false);
        reset();
        myChanges.firePropertyChange(ChangeEvents.START_RECORDING.toString(), null, true);
    }

    /**
     * Handles the stop button
     */
    @FXML
    private void handleStopBtn() {
        myChanges.firePropertyChange(ChangeEvents.STOP_RECORDING.name(), null, true);
        myRecordBtn.setDisable(false);
        myAnalyzeButton.setDisable(false);
        myStopBtn.setDisable(true);
    }

    /**
     * Handles the exit button
     */
    @FXML
    private void handleCloseBtn() {
        myStage = (Stage) myCloseBtn.getScene().getWindow();
        myChanges.firePropertyChange(ChangeEvents.STOP_RECORDING.name(), null, true);
        final FXMLLoader sfa_fxml = new FXMLLoader(AudioRecordingController.class.getResource("/com/kass/vocalanalysistool/gui/SelectAudioFile.fxml"));
        try {
            final Scene sfa_scene = new Scene(sfa_fxml.load());
            final Stage sfa_stage = new Stage();
            sfa_stage.setScene(sfa_scene);
            sfa_stage.setTitle("Select Audio File");
            sfa_stage.getIcons().add(new Image(Objects.requireNonNull(getClass().
                    getResourceAsStream("/com/kass/vocalanalysistool/icons" +
                            "/vocal_analysis_icon.png"))));
            sfa_stage.show();
            myStage.close();

        } catch (final IOException theEvent) {
            throw new RuntimeException("The scene failed to load!");
        }

        reset();
        MY_RECORDER.removePropertyChangeListener(this);

    }

    @FXML
    private void handleAnalyzeBtn() {
        try {
            final Path audioPath = Path.of(System.getProperty("user.home"),
                    "VocalAnalysisTool", "Vocal_Sample.wav");

            if(!Files.exists(audioPath)) {
                throw new IOException("The file path can not be found!");
            }

            MY_RUNNER_SERVICE.runScript(String.valueOf(audioPath));
            myStage = (Stage) myAnalyzeButton.getScene().getWindow();
            MY_RECORDER.removePropertyChangeListener(this);
            myStage.close();


        } catch (final IOException theEvent) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Vocal Sample audio file not found!");
            alert.setContentText("Something happened and was unable to locate the audio " +
                    "sample");
            alert.showAndWait();
            System.exit(1);
        }

    }

    /**
     * Resets the state to default.
     */
    private void reset() {
        myHourLabel.setText("00");
        myMinuteLabel.setText("00");
        mySecondsLabel.setText("00");

    }

    /**
     * Adds theListener to this property change support list.
     *
     * @param theListener The component listening for changes.
     */
    public void addPropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.addPropertyChangeListener(theListener);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {

        switch (theEvent.getPropertyName()) {
            case "HOUR": {
                myHourLabel.setText(String.valueOf(theEvent.getNewValue()));
                break;
            }
            case "MIN": {
                myMinuteLabel.setText(String.valueOf(theEvent.getNewValue()));
                break;
            }
            case "SEC": {
                mySecondsLabel.setText(String.valueOf(theEvent.getNewValue()));
                break;
            }
            case "WORKFLOW_RESULT": {
                OpenAudioDataScene.openAnalysis(theEvent);
            }

        }
    }
}
