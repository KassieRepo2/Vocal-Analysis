package com.kass.vocalanalysistool.common;

public enum WorkflowResult {

    /**
     * The python script execution was successful
     */
    SUCCESS,

    /**
     * The python script execution failed.
     */
    FAILED,

    /**
     * The python script execution was canceled or interrupted
     */
    CANCELLED,

    /**
     * Invalid recorded sample
     */
    INVALID,
}
