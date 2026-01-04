package com.kass.vocalanalysistool.workflow;

import com.kass.vocalanalysistool.common.StageNames;
import com.kass.vocalanalysistool.common.WorkflowResult;
import com.kass.vocalanalysistool.view.UsersAnalysisController;
import com.kass.vocalanalysistool.view.util.StageFactory;
import com.kass.vocalanalysistool.view.AudioDataController;
import com.kass.vocalanalysistool.view.AudioRecordingController;
import com.kass.vocalanalysistool.view.SelectAudioFileController;
import com.kass.vocalanalysistool.view.util.StageRegistry;
import java.beans.PropertyChangeEvent;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;


/**
 * Opens the audio data analysis scene.
 * <p><b>Only use if the python script returned successfully</b></p>
 *
 * @author Kassie Whitney
 * @version 12/26/2025
 */
public class OpenAudioDataScene {

    private static final Logger logger = Logger.getLogger(OpenAudioDataScene.class.getName());

    /**
     * Static class: Constructor not used.
     */
    private OpenAudioDataScene() {
    }

    /**
     * Opens the analysis scene once the vocal data has been processed and the python runner
     * service returned successfully
     */
    public static void openAnalysis(final PropertyChangeEvent theEvent) {

        if (StageRegistry.isOpen(StageNames.AUDIO_DATA.name())) {
                StageRegistry.getStage(StageNames.AUDIO_DATA.name()).close();
            }
            if (StageRegistry.isOpen(StageNames.USER_ANALYSIS.name())) {
                StageRegistry.getStage(StageNames.USER_ANALYSIS.name()).close();
            }

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> openAnalysis(theEvent));
            return;
        }

        logger.log(Level.INFO, "[Open Analysis] Opened the analysis class");



        final WorkflowResult result = (WorkflowResult) theEvent.getNewValue();


        if (result.equals(WorkflowResult.FAILED) || result.equals(WorkflowResult.CANCELLED)) {

            logger.log(Level.SEVERE, "[Open Analysis] Work Flow Failed");
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            final Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
            setIconToStage(alertStage);
            alertStage.toFront();
            alertStage.isAlwaysOnTop();

            alert.setAlertType(Alert.AlertType.ERROR);
            alert.setTitle("Failed To Process");
            alert.setContentText("""
                    The audio recording could not be processed!
                    
                    The CSV file might still be open or the audio file might
                    have gotten corrupted or the audio file is not in a .wav format.
                    """);


            logger.log(Level.SEVERE, "[Open Analysis] Building new SAF stage");

            StageRegistry.show(StageNames.SELECT_AUDIO.name(), () ->
                    StageFactory.buildStage(new SelectAudioFileController(),
                            "SelectAudioFile.fxml",
                            "Select Audio File",
                            "/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png",
                            false));
            alert.showAndWait();
        } else if (result.equals(WorkflowResult.INVALID)) {

            logger.log(Level.SEVERE, "[Open Analysis] Invalid work flow result");
            final Alert alert = new Alert(Alert.AlertType.WARNING);
            final Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
            alert.setAlertType(Alert.AlertType.WARNING);
            alert.setHeaderText("Invalid Recording");
            setIconToStage(alertStage);
            alert.setContentText("""
                    The recorder couldn't detect any valid acoustics.
                    
                    Check your microphone settings and try again!
                    """);


            StageRegistry.show(StageNames.VOICE_RECORDING.name(), () ->
                    StageFactory.buildStage(new AudioRecordingController(),
                            "AudioRecording.fxml",
                            "Voice Recorder",
                            "/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png",
                            false));

            alert.showAndWait();
            alertStage.toFront();

        } else if (result.equals(WorkflowResult.SUCCESS)) {

            logger.log(Level.INFO, "[Open Analysis] Building new Audio Data Controller stage");

            final Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

            double screenCenterX = screenBounds.getMinX() + screenBounds.getWidth() / 2;
            double screenCenterY = screenBounds.getMinY() + screenBounds.getHeight() / 2;

            double adjustmentX = .75;
            double adjustmentY = .5;

            double windowX = screenCenterX - (screenCenterX * adjustmentX);
            double windowY = screenCenterY - (screenCenterY * adjustmentY);



            final Stage audioDataController = StageRegistry.show(StageNames.AUDIO_DATA.name(),
                    () ->
                            StageFactory.buildStage(new AudioDataController(),
                                    "AudioData.fxml",
                                    "Analysis Results",
                                    "/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png",
                                    false,
                                    windowX,
                                    windowY));

            logger.log(Level.INFO, "[Open Analysis] Opening audio data controller stage");

            StageRegistry.show(StageNames.USER_ANALYSIS.name(), () ->
                    StageFactory.buildStage(new UsersAnalysisController(),
                            "UsersAnalysis.fxml",
                            "User Data Summary",
                            false,
                            (audioDataController.getX() + audioDataController.getWidth() + 10),
                            audioDataController.getY())
            );
            if (StageRegistry.isOpen(StageNames.VOICE_RECORDING.name())) {
                final Stage recorder =
                        StageRegistry.getStage(StageNames.VOICE_RECORDING.name());
                recorder.toFront();
                recorder.setY(recorder.getHeight() - 300);
            }

        }
    }

    /**
     * Sets the icon to the stage.
     *
     * @param theStage the new stage being initialized.
     */
    private static void setIconToStage(final Stage theStage) {
        theStage.getIcons().add(new Image(Objects.requireNonNull
                (OpenAudioDataScene.class.getResourceAsStream
                        ("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));
    }
}
