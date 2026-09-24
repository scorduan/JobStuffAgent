package org.example.jobstuffagent.domain;

/**
 * The authoritative lifecycle state of a job application.
 */
public enum ApplicationStatus {
    RECOMMENDED,
    APPLIED,
    INTERVIEWING,
    OFFERED,
    ACCEPTED,
    STALE,
    PRESUMED_DEAD,
    REJECTED,
    DECLINED
}
