package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.workflow.PythonRunnerService;
import com.kass.vocalanalysistool.common.ChangeEvents;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

/**
 * Shows the loading screen whenever an audio file gets selected.
 *
 * @author Kassie Whitney
 * @version 9.3.25
 */
public class LoadingScreenController implements PropertyChangeListener {

    @FXML
    private Label myProgressLabel;
    /**
     * The progress bar element.
     */
    @FXML
    private ProgressBar myProgBar;

    /**
     * Used to the position of the current line of the python script
     *
     * @param theScript the PythonRunnerService scene
     */
    public void addPropertyChangeListener(final PythonRunnerService theScript) {
        theScript.addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {
        Platform.runLater(() -> {
            if (theEvent.getPropertyName().equals(ChangeEvents.UPDATE_PROGRESS.toString())) {
                myProgBar.setProgress((double) theEvent.getNewValue());
                myProgressLabel.setText(String.valueOf(theEvent.getOldValue()));
            }
        });
    }
}
