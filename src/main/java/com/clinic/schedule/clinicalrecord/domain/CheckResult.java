package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;

/**
 * The result of a check: who reported it, when, and what it says. It may come from someone other
 * than whoever did the check — the analysis of a blood sample, say (D-53).
 *
 * <p>Free text today, in a class of its own so that giving results a structure later changes
 * this class and not the check.
 */
public record CheckResult(PractitionerId author, LocalDateTime writtenAt, String text) {

    /** As long as the column that stores it; a laboratory report can be long (D-38). */
    public static final int MAX_TEXT_LENGTH = 10_000;

    public CheckResult {
        if (author == null || writtenAt == null) {
            throw new IllegalArgumentException("A result needs an author and a time");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A result needs a text");
        }
        text = text.strip();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("A result is at most " + MAX_TEXT_LENGTH + " characters");
        }
    }
}
