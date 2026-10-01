package com.clinic.schedule.appointment.domain;

import java.time.LocalDateTime;

/**
 * What is recorded of a cancellation (D-76): when, by whom, how, who asked for it, and an optional
 * note. A time off cancels on the practice's behalf, so it is always the practice that asked.
 *
 * @param by the username of whoever cancelled it — for a time off, whoever recorded it
 * @param note reception's own words, or null; blank is taken as none
 */
public record Cancellation(LocalDateTime at, String by, CancellationOrigin origin, CancellationRequester requestedBy,
        String note) {

    /** As long as the column that stores it (D-38). */
    static final int MAX_NOTE_LENGTH = 200;

    public Cancellation {
        if (at == null || by == null || by.isBlank() || origin == null || requestedBy == null) {
            throw new IllegalArgumentException("A cancellation needs when, who, how and who asked for it");
        }
        if (origin == CancellationOrigin.TIME_OFF && requestedBy != CancellationRequester.PRACTICE) {
            throw new IllegalArgumentException("A cancellation by time off is asked for by the practice");
        }
        note = note == null || note.isBlank() ? null : note.strip();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("A cancellation note is at most " + MAX_NOTE_LENGTH + " characters");
        }
    }

    /** Reception cancelled it, as the patient or the practice asked. */
    public static Cancellation byHand(LocalDateTime at, String by, CancellationRequester requestedBy, String note) {
        return new Cancellation(at, by, CancellationOrigin.BY_HAND, requestedBy, note);
    }

    /** A time off cancelled it, as {@code by} recorded it. */
    public static Cancellation byTimeOff(LocalDateTime at, String by) {
        return new Cancellation(at, by, CancellationOrigin.TIME_OFF, CancellationRequester.PRACTICE, null);
    }
}
