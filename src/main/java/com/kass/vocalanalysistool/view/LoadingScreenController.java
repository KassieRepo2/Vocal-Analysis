package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.common.ChangeEvents;
import com.kass.vocalanalysistool.controller.Main;
import com.kass.vocalanalysistool.workflow.PythonRunnerService;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;

/**
 * Shows the loading screen whenever an audio file gets selected.
 *
 * @author Kassie Whitney
 * @version 9.3.25
 */
public class LoadingScreenController implements PropertyChangeListener {

    /**
     * The new installation label.
     */
    @FXML
    private Label myInstallLabel;

    /**
     * The general progress label
     */
    @FXML
    private Label myProgressLabel;
    /**
     * The progress bar element.
     */
    @FXML
    private ProgressBar myProgBar;

    /**
     * The property change listener.
     */
    private final PropertyChangeSupport myChanges = new PropertyChangeSupport(this);

    @FXML
    private void initialize() {
        myProgBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        myProgBar.getStyleClass().add("shimmer-bar");
    }

    /**
     * Used to update the progress bar dictated by the current position in the script.
     * Also adds the runner service as a listener to this progress bar to pass data from the
     * main.
     *
     * @param theScript the PythonRunnerService scene
     */
    public void addLoadingScreenAsListener(final PythonRunnerService theScript) {
        theScript.addPropertyChangeListener(this);
        this.addPropertyChangeListener(theScript);
    }

    /**
     * Adds the Main to this components listener list.
     *
     * @param theController The main controller.
     */
    public void addMainToChangeListener(final Main theController) {
        theController.addPropertyChangeListener(this);

    }

    /**
     * Adds the components to this components listener lists.
     *
     * @param theListener The other component.
     */
    private void addPropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.addPropertyChangeListener(theListener);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {
        Platform.runLater(() -> {


            if (theEvent.getPropertyName().equals(ChangeEvents.NEW_INSTALL_B.toString())) {
                myProgBar.setProgress((double) theEvent.getNewValue());
                myProgressLabel.setText(String.valueOf(theEvent.getOldValue()));
            }
            if (theEvent.getPropertyName().equals(ChangeEvents.NEW_INSTALL_A.name())) {
                myInstallLabel.setText("Setting up a new environment:");
            }

            if (theEvent.getPropertyName().equals(ChangeEvents.UPDATE_PROGRESS.name())) {
                myProgBar.setProgress((double) theEvent.getNewValue());
                myProgressLabel.setText(" ");
                myInstallLabel.setText(String.valueOf(theEvent.getOldValue()));
            }

        });
    }
}
