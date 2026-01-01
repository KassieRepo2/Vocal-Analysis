package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.common.ChangeEvents;
import com.kass.vocalanalysistool.common.StageNames;
import com.kass.vocalanalysistool.view.util.StageFactory;
import com.kass.vocalanalysistool.view.util.StageRegistry;
import com.kass.vocalanalysistool.workflow.OpenAudioDataScene;
import com.kass.vocalanalysistool.workflow.PythonRunnerService;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * <p>Audio file selector. </p>
 *
 * <p> Audio file must be a [.wav] format. </p>
 *
 * @author Kassie Whitney
 * @version 9.3.25
 */
public class SelectAudioFileController implements PropertyChangeListener {


    /**
     * The logger object for debugging.
     */
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Opens the record scene
     */
    @FXML
    public Button myRecordBtn;

    /**
     * The open file button
     */
    @FXML
    private Button myOpenFileButton;

    /**
     * The exit button
     */
    @FXML
    private Button myExitButton;

    /**
     * The python script utilities class.
     */
    private final PythonRunnerService myPyScript = new PythonRunnerService();

    /**
     * Used to initialize certain features.
     */
    @FXML
    private void initialize() {
        myPyScript.addPropertyChangeListener(this);
    }

    /**
     * Opens the filechooser window where the user is able to choose the audio file.
     */
    @FXML
    private void handleOpenFile() throws IOException {

        logger.setLevel(Level.INFO);

        final FileChooser fileChooser = new FileChooser();
        final Stage thisStage = (Stage) myOpenFileButton.getScene().getWindow();

        fileChooser.setTitle("Select Audio File");

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("WAV Audio Files", "*.wav"),
                new FileChooser.ExtensionFilter("AIFF Audio Files", "*.aiff"));

        final File file = fileChooser.showOpenDialog(thisStage);

        if (file != null) {
            final String path = file.getAbsolutePath();

            logger.info(() -> "Path: " + path);

            myPyScript.runScript(path);
            thisStage.hide();
        }
    }


    /**
     * Handles the action event for the record new audio button.
     */
    @FXML
    private void handleRecordNewAudio() {
        final Stage thisStage = (Stage) myRecordBtn.getScene().getWindow();

        final Stage recorderStage = StageRegistry.show(StageNames.VOICE_RECORDING.name(), () ->
                StageFactory.buildStage(this,
                        "AudioRecording.fxml",
                        "Voice Recorder",
                        false));
        recorderStage.toFront();
        thisStage.hide();
        myPyScript.removePropertyChangeListener(this);
    }

    /**
     * Handles the exit of the stage
     */
    @FXML
    private void handleExit() {
        Stage exitStage = (Stage) myExitButton.getScene().getWindow();
        exitStage.close();
        myPyScript.removePropertyChangeListener(this);
    }

    /**
     * Handles property change events.
     *
     * @param theEvent A PropertyChangeEvent object describing the event source
     *                 and the property that has changed.
     */
    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {
        if (ChangeEvents.WORKFLOW_RESULT.name().equals(theEvent.getPropertyName())) {
            Platform.runLater(() -> {
                logger.log(Level.INFO, "Opening Audio DataScene");
                OpenAudioDataScene.openAnalysis(theEvent);
            });
            myPyScript.removePropertyChangeListener(this);
        }
    }


}
