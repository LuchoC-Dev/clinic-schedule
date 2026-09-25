package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;

/**
 * The result of a check: who reported it, when, and what it says. It may come from someone other
 * than whoever did the check — the analysis of a blood sample, say (D-53).
 *
 * <p>Free text today, in a class of its own so that giving results a structure later changes
 * this class and not the check. The text may be left out when the check has an attachment that
 * says it — a laboratory's PDF — so {@code text} is null then (D-57).
 */
public record CheckResult(PractitionerId author, LocalDateTime writtenAt, String text) {

    /** As long as the column that stores it; a laboratory report can be long (D-38). */
    public static final int MAX_TEXT_LENGTH = 10_000;

    public CheckResult {
        if (author == null || writtenAt == null) {
            throw new IllegalArgumentException("A result needs an author and a time");
        }
        text = text == null || text.isBlank() ? null : text.strip();
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("A result is at most " + MAX_TEXT_LENGTH + " characters");
        }
    }

    /** Whether it says something in words; a result may be only the check's attachments (D-57). */
    public boolean hasText() {
        return text != null;
    }
}
