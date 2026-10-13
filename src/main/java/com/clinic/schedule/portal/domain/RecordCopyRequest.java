package com.clinic.schedule.portal.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A copy of a clinical record asked for on the web, as the patient follows it (D-95): whose, who asked and when,
 * how it is delivered, by when it is due, and when it was authenticated and handed over. The record itself is the
 * clinical record module's: the patient gets it as a PDF, or on paper.
 *
 * @param authenticatedAt null while a practitioner has not authenticated it
 * @param handedOverAt null unless reception handed the envelope over
 */
public record RecordCopyRequest(UUID id, UUID patientId, UUID requestedBy, LocalDateTime requestedAt, Delivery delivery,
        LocalDateTime deadline, LocalDateTime authenticatedAt, LocalDateTime handedOverAt) {

    /** How the patient chose to get it. */
    public enum Delivery {
        /** A PDF to download from the account. */
        PDF,
        /** On paper, collected at reception. */
        PAPER
    }

    /** Where it stands, as the patient reads it. */
    public enum State {
        /** A practitioner has not authenticated it yet. */
        WAITING,
        /** Authenticated, to download. */
        READY_TO_DOWNLOAD,
        /** Authenticated, on paper at reception. */
        READY_TO_COLLECT,
        /** Collected at reception. */
        HANDED_OVER
    }

    public State state() {
        if (authenticatedAt == null) {
            return State.WAITING;
        }
        if (delivery == Delivery.PDF) {
            return State.READY_TO_DOWNLOAD;
        }
        return handedOverAt == null ? State.READY_TO_COLLECT : State.HANDED_OVER;
    }
}
