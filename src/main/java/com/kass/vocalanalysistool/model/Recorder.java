package com.kass.vocalanalysistool.model;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class Recorder implements PropertyChangeListener {

    final private PropertyChangeSupport myChanges = new PropertyChangeSupport(this);

    public Recorder() {

    }

    public void startTimer() {

    }

    public void startRecording() {

    }

    /**
     * Adds an external component as a listener to this model.
     * @param theListener The external component that's listening for new changes.
     */
    public void addPropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.addPropertyChangeListener(theListener);
    }


    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {

    }
}
