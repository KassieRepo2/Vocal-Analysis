package com.kass.vocalanalysistool.workflow;

import com.kass.vocalanalysistool.common.WorkflowResult;
import com.kass.vocalanalysistool.view.util.StageFactory;
import com.kass.vocalanalysistool.view.AudioDataController;
import com.kass.vocalanalysistool.view.AudioRecordingController;
import com.kass.vocalanalysistool.view.SelectAudioFileController;
import java.beans.PropertyChangeEvent;
import java.util.Objects;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;


/**
 * Opens the audio data analysis scene.
 * <p><b>Only use if the python script returned successfully</b></p>
 *
 * @author Kassie Whitney
 * @version 12/26/2025
 */
public class OpenAudioDataScene {

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

        Alert alert = new Alert(Alert.AlertType.ERROR);
        final Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
        setIconToStage(alertStage);

        final WorkflowResult result = (WorkflowResult) theEvent.getNewValue();

        if (result.equals(WorkflowResult.FAILED) || result.equals(WorkflowResult.CANCELLED)) {


            alert.setAlertType(Alert.AlertType.ERROR);
            alert.setTitle("Failed To Process");
            alert.setContentText("""
                    The audio recording could not be processed!
                    
                    The CSV file might still be open or the audio file might
                    have gotten corrupted or the audio file is not in a .wav format.
                    """);

            alert.showAndWait();

            final Stage safStage = StageFactory.buildStage(new SelectAudioFileController(),
                    "SelectAudioFile.fxml",
                    "Select Audio File",
                    "/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png",
                    false);

            safStage.show();

        } else if (result.equals(WorkflowResult.INVALID)) {
            alert = new Alert(Alert.AlertType.WARNING);
            alert.setAlertType(Alert.AlertType.WARNING);
            alert.setHeaderText("Invalid Recording");
            alert.setContentText("""
                    The recorder couldn't detect any valid acoustics.
                    
                    Check your microphone settings and try again!
                    """);
            alert.showAndWait();

            final Stage recorderStage =
                    StageFactory.buildStage(new AudioRecordingController(),
                            "AudioRecording.fxml",
                            "Voice Recorder",
                            "/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png",
                            false);

            recorderStage.show();
        } else {

            final Stage audioDataController =
                    StageFactory.buildStage(new AudioDataController(),
                            "AudioData.fxml",
                            "Analysis Results",
                            "/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png",
                            false);

            audioDataController.show();
        }

    }

    /**
     * Sets the icon to the stage.
     * @param theStage the new stage being initialized.
     */
    private static void setIconToStage(final Stage theStage) {
        theStage.getIcons().add(new Image(Objects.requireNonNull
                        (OpenAudioDataScene.class.getResourceAsStream
                                ("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));
    }
}
