package org.example.jobstuffagent.application;

/**
 * Lifecycle states for one bounded workflow execution attempt.
 */
public enum AgentSessionStatus {
    RECEIVED,
    CLASSIFYING,
    LOOKING_UP_DATA,
    PLANNING,
    VALIDATING,
    EXECUTING,
    COMPLETED,
    COMPLETED_NEEDS_INPUT,
    FAILED
}
