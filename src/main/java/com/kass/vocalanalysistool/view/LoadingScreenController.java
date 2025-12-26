package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.util.PythonScript;
import com.kass.vocalanalysistool.common.Properties;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;

/**
 * Shows the loading screen whenever an audio file gets selected.
 *
 * @author Kassie Whitney
 * @version 9.3.25
 */
public class LoadingScreenController implements PropertyChangeListener {

    /**
     * The progress bar element.
     */
    @FXML
    private ProgressBar myProgBar;

    /**
     * Used to the position of the current line of the python script
     * @param theScript the PythonScript scene
     */
    public void addPropertyChangeListener(final PythonScript theScript) {
        theScript.addPropertyListener(this);
    }

    /**
     * Gets the current progress status.
     * @return Data used to progress the progress bar.
     */
    public double getProgressStatus() {
        return myProgBar.getProgress();
    }

    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {
        if(theEvent.getPropertyName().equals(Properties.UPDATE_PROGRESS.toString())) {
            myProgBar.setProgress((double) theEvent.getNewValue());
        }
    }
}
