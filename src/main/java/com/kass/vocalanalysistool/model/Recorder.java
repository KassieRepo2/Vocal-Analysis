package com.kass.vocalanalysistool.model;

import com.kass.vocalanalysistool.common.ChangeEvents;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Alert;
import javafx.util.Duration;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * Records vocal audio with the users default microphone.
 *
 * @author  Kassie Whitney
 * @version 12/25/2025
 */
public class Recorder implements PropertyChangeListener {

    /**
     * Property change support for this class
     */
    private final PropertyChangeSupport myChanges = new PropertyChangeSupport(this);

    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(Recorder.class.getName());

    /**
     * Increments the second counter (TICK)
     */
    private Timeline myTimeLine;

    /**
     * The current hour value for the recording
     */
    private static int HOUR = 0;

    /**
     * The current minute value for the recording
     */
    private static int MIN = 0;

    /**
     * The current second value for the recording
     */
    private static int SEC = 0;

    /**
     * Audio recording format
     */
    private static final AudioFormat FORMAT = new AudioFormat(
            44100.0f, // sample rate
            16, // sample size in bits
            1, // number of channels (1=mono)
            true, // signed
            false // little-endian
    );

    /**
     * Provides specific info about the Audio format to the Audio Line
     */
    private static final DataLine.Info INFO = new DataLine.Info(TargetDataLine.class, FORMAT);

    /**
     * The audio Line
     */
    private static TargetDataLine LINE;

    /**
     * Reads bytes from the live mic line
     */
    private static AudioInputStream INPUT_STREAM;

    /**
     * The file that the audio data will be recorded on
     */
    private static File AUDIO_FILE;

    /**
     * The constructor
     */
    public Recorder() {
        try {

            LINE = (TargetDataLine) AudioSystem.getLine(INFO);

            INPUT_STREAM = new AudioInputStream(LINE);

            AUDIO_FILE = new File("Vocal_Sample.wav");

        } catch (final LineUnavailableException theException) {
            throwLineError();
        }
    }

    /**
     * Starts the audio timer.
     */
    public void startTimer() {

        if (myTimeLine == null) {
            myTimeLine = new Timeline(new KeyFrame(Duration.seconds(1
            ), theEvent -> tick()));

            myTimeLine.setCycleCount(Animation.INDEFINITE);
            myTimeLine.play();
        }
    }


    /**
     * Increments the seconds field
     */
    private void tick() {
        SEC++;
        myChanges.firePropertyChange(ChangeEvents.SEC.toString(), null, numToString(SEC));
        if (SEC == 60) {
            SEC = 0;
            MIN++;
            myChanges.firePropertyChange(ChangeEvents.SEC.toString(), null, numToString(SEC));
        }

        if (MIN == 60) {
            MIN = 0;
            HOUR++;
            myChanges.firePropertyChange(ChangeEvents.MIN.toString(), null, numToString(MIN));
            myChanges.firePropertyChange(ChangeEvents.HOUR.toString(), null, numToString(HOUR));
        }
        myChanges.firePropertyChange(ChangeEvents.MIN.toString(), null, numToString(MIN));
    }


    /**
     * Stops the timer and resets the clock
     */
    private void stopTimer() {
        if (myTimeLine != null) {
            myTimeLine.stop();
            myTimeLine = null;
            HOUR = 0;
            MIN = 0;
            SEC = 0;
        }
    }


    /**
     * Starts the audio recording.
     */
    public void startRecording() {
        try {
            LINE.open(FORMAT);
            LINE.start();

            Thread writer = new Thread(() -> {
                try {

                    AudioSystem.write(INPUT_STREAM, AudioFileFormat.Type.WAVE, AUDIO_FILE);
                } catch (final IOException theException) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Invalid Sound File");
                    alert.setContentText("Something went wrong and was unable to create a " +
                            "new audio file!");
                    alert.show();
                }
            }, "wav-writer");

            writer.start();

        } catch (LineUnavailableException e) {
            throwLineError();
        }
    }


    /**
     * Stops the recording.
     */
    public void stopRecording() {
        LINE.stop();
        stopTimer();
    }


    /**
     * Converts the integer timer digit into a formatted string.
     *
     * @param num the timer digit.
     * @return the formated string of the digit.
     */
    private String numToString(final int num) {
        String result;
        if (num < 10) {
            result = "0" + num;
        } else {
            result = String.valueOf(num);
        }

        return result;
    }


    /**
     * Throws an error and an alert message box if the line becomes invalid.
     */
    private void throwLineError() {
        LOGGER.log(Level.SEVERE, "The line was unable to be initialized");
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Invalid Microphone Line");
        alert.setContentText("The current microphone selected is invalid!\nPlease " +
                "change your input device and try again!");
        alert.show();
        throw new RuntimeException("Microphone Unavailable");
    }


    /**
     * Adds an external component as a listener to this model.
     *
     * @param theListener The external component that's listening for new changes.
     */
    public void addPropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.addPropertyChangeListener(theListener);

    }

    /**
     * Adds the seen as a listener for this scene's changes.
     * @param theListener The scene that needs to be notified of changes.
     */
    public void removePropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.removePropertyChangeListener(theListener);
    }


    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {
        switch (theEvent.getPropertyName()) {
            case "START_RECORDING": {
                startTimer();
                startRecording();
                break;
            }
            case "STOP_RECORDING": {
                stopTimer();
                stopRecording();
            }
        }
    }
}
