package com.kass.vocalanalysistool.common;

public enum ChangeEvents {
    /**
     * Notifies the progress bar to update x amount
     */
    UPDATE_PROGRESS,

    /**
     * New Installation of the environment
     */
    NEW_INSTALL_A,

    /**
     *
     */
    NEW_INSTALL_B,

    /**
     * New installation Skipped
     */
    SKIP_INSTALL,


    /**
     * Notifies the Model when the recording has started
     */
    START_RECORDING,

    /**
     * Notifies the model when the recording has stopped
     */
    STOP_RECORDING,

    /**
     * Notifies the viewer the status of the audio recording
     */
    PLAY_STATUS,

    /**
     * Notifies the model when the playback button was pressed
     */
    PLAY_RECORDING,

    /**
     * The updated hour
     */
    HOUR,

    /**
     * The updated minute
     */
    MIN,

    /**
     * The updated second.
     */
    SEC,

    /**
     * The workFlow Result
     */
    WORKFLOW_RESULT,
}
