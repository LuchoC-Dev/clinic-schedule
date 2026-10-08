package com.clinic.schedule.cashdesk.domain;

/**
 * What the cash desk needs of a patient to cover their visit (D-90): whether reception saw their document, and
 * their health insurance, as they stand now.
 *
 * @param insurance null when the patient has none recorded
 */
public record Insured(boolean identityVerified, Insurance insurance) {
}
