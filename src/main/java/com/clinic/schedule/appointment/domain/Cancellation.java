package com.clinic.schedule.appointment.domain;

import java.time.LocalDateTime;

/**
 * What is recorded of a cancellation (D-76): when, by whom, how, who asked for it, and an optional
 * note. A time off cancels on the practice's behalf, so it is always the practice that asked; on the web
 * or from an email's link, it is always the patient (D-83).
 *
 * @param by the username of whoever cancelled it — for a time off, whoever recorded it; on the web, the
 *        patient's account; from an email's link, {@link #EMAIL_LINK_BY}, since nobody can tell who held it
 * @param note reception's own words, or null; blank is taken as none
 */
public record Cancellation(LocalDateTime at, String by, CancellationOrigin origin, CancellationRequester requestedBy,
        String note) {

    /** As long as the column that stores it (D-38). */
    static final int MAX_NOTE_LENGTH = 200;

    /** Who cancelled from an email's link: whoever held it, whom nothing tells (D-83). */
    public static final String EMAIL_LINK_BY = "email-link";

    public Cancellation {
        if (at == null || by == null || by.isBlank() || origin == null || requestedBy == null) {
            throw new IllegalArgumentException("A cancellation needs when, who, how and who asked for it");
        }
        if (origin == CancellationOrigin.TIME_OFF && requestedBy != CancellationRequester.PRACTICE) {
            throw new IllegalArgumentException("A cancellation by time off is asked for by the practice");
        }
        if ((origin == CancellationOrigin.ONLINE || origin == CancellationOrigin.EMAIL_LINK)
                && requestedBy != CancellationRequester.PATIENT) {
            throw new IllegalArgumentException("A cancellation from the web is asked for by the patient");
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

    /** The patient cancelled it from their web account, named by {@code by} (D-83). */
    public static Cancellation onTheWeb(LocalDateTime at, String by) {
        return new Cancellation(at, by, CancellationOrigin.ONLINE, CancellationRequester.PATIENT, null);
    }

    /** Someone cancelled it from an email's link, on the patient's behalf (D-83). */
    public static Cancellation byEmailLink(LocalDateTime at) {
        return new Cancellation(at, EMAIL_LINK_BY, CancellationOrigin.EMAIL_LINK, CancellationRequester.PATIENT,
                null);
    }

    /** A time off cancelled it, as {@code by} recorded it. */
    public static Cancellation byTimeOff(LocalDateTime at, String by) {
        return new Cancellation(at, by, CancellationOrigin.TIME_OFF, CancellationRequester.PRACTICE, null);
    }
}
