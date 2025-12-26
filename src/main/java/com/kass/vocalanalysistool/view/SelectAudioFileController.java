package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.workflow.PythonRunnerService;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * <p>Audio file selector. </p>
 *
 * <p> Audio file must be a [.wav] format. </p>
 *
 * <p>Use the provided Audacity software to record your voice. </p>
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
    private final static PythonRunnerService myPyScript = new PythonRunnerService();

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

            thisStage.close();
            myPyScript.runScript(path);
        }
    }

    /**
     * Opens the data analysis screen.
     */
    private void openAudioDataScene() {
        try {
                final FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/kass" +
                        "/vocalanalysistool/gui/AudioData.fxml"));
                final Parent root = loader.load();

                final Stage audioDataController = new Stage();
                audioDataController.setTitle("Analysis Results");
                audioDataController.setScene(new Scene(root));
                audioDataController.show();
                audioDataController.setResizable(false);
                audioDataController.getIcons().add(new Image(Objects.requireNonNull
                        (getClass().getResourceAsStream
                                ("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));

            } catch (final IOException theException) {

                logger.log(Level.SEVERE, "No File Found!", theException);

            }
    }

    /**
     * Handles the action event for the record new audio button.
     */
    @FXML
    private void handleRecordNewAudio() {
        try {
            final FXMLLoader fxmlLoader =
                    new FXMLLoader(SelectAudioFileController.class.getResource("/com/kass" +
                            "/vocalanalysistool/gui/AudioRecording.fxml"));

            final Scene recorderScene = new Scene(fxmlLoader.load());

            final Stage audioRecorderStage = new Stage();

            audioRecorderStage.setScene(recorderScene);
            audioRecorderStage.setTitle("Voice Recorder");
            audioRecorderStage.getIcons().add(new Image(Objects.requireNonNull(getClass().
                    getResourceAsStream("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));
            audioRecorderStage.show();
            audioRecorderStage.setResizable(false);

            Stage thisStage = (Stage) myRecordBtn.getScene().getWindow();
            thisStage.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles the exit of the stage
     */
    @FXML
    private void handleExit() {
        Stage exitStage = (Stage) myExitButton.getScene().getWindow();
        exitStage.close();
    }


    /**
     * Handles property change events.
     *
     * @param theEvent A PropertyChangeEvent object describing the event source
     *                 and the property that has changed.
     */
    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {
        switch(theEvent.getPropertyName()) {
            case "SUCCESS" -> openAudioDataScene();
            case "FAILED" -> {
                final Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Failed");
                alert.setContentText("There was an error in the python script!");
                alert.showAndWait();
            }
        }

    }

}
