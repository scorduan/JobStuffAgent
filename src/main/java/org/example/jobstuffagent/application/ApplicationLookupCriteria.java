package org.example.jobstuffagent.application;

/**
 * Structured criteria for a deterministic application lookup.
 */
public record ApplicationLookupCriteria(String company, String role) {

    public ApplicationLookupCriteria {
        company = normalize(company);
        role = normalize(role);
    }

    public static ApplicationLookupCriteria none() {
        return new ApplicationLookupCriteria(null, null);
    }

    public boolean hasCriteria() {
        return company != null || role != null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
