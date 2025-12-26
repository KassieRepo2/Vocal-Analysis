package com.kass.vocalanalysistool.workflow;

import com.kass.vocalanalysistool.common.WorkflowResult;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
        final Alert alert = new Alert(Alert.AlertType.ERROR);

        final WorkflowResult result = (WorkflowResult) theEvent.getNewValue();

        if (result.equals(WorkflowResult.FAILED) || result.equals(WorkflowResult.CANCELLED)) {

            alert.setTitle("Failed");
            alert.setContentText("There was an error in the python script!");
            alert.showAndWait();
            throw new IllegalStateException("The python script had failed!");
        } else {
            final Logger logger = Logger.getLogger(OpenAudioDataScene.class.getName());
            try {
                final FXMLLoader loader = new FXMLLoader(OpenAudioDataScene.class.getResource("/com/kass" +
                        "/vocalanalysistool/gui/AudioData.fxml"));
                final Parent root = loader.load();
                final Stage audioDataController = new Stage();
                audioDataController.setTitle("Analysis Results");
                audioDataController.setScene(new Scene(root));
                audioDataController.show();
                audioDataController.setResizable(false);
                audioDataController.getIcons().add(new Image(Objects.requireNonNull
                        (OpenAudioDataScene.class.getResourceAsStream
                                ("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));

            } catch (final IOException theException) {

                logger.log(Level.SEVERE, "No File Found!", theException);
            }
        }

    }
}
