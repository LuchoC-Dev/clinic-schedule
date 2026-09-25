package com.clinic.schedule.consultation.domain;

import java.time.LocalDateTime;

/**
 * A note added to a consultation after it was sent: who wrote it, when, and what it says. It is
 * how a consultation is corrected or completed, since what was written never changes (D-52).
 *
 * <p>It is about the consultation as a whole: an item it refers to is named in the text.
 */
public record Addendum(AddendumId id, PractitionerId author, LocalDateTime writtenAt, String text) {

    /** As long as the column that stores it, the same as an item (D-38). */
    public static final int MAX_TEXT_LENGTH = 2_000;

    public Addendum {
        if (id == null || author == null || writtenAt == null) {
            throw new IllegalArgumentException("An addendum needs an id, an author and a time");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("An addendum needs a text");
        }
        text = text.strip();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("An addendum is at most " + MAX_TEXT_LENGTH + " characters");
        }
    }
}
