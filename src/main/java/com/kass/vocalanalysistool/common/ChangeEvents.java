package com.kass.vocalanalysistool.common;

public enum ChangeEvents {
    /**
     * Notifies the progress bar to update x amount
     */
    UPDATE_PROGRESS,

    /**
     * Notifies the Model when the recording has started
     */
    START_RECORDING,

    /**
     * Notifies the model when the recording has stopped
     */
    STOP_RECORDING,

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
