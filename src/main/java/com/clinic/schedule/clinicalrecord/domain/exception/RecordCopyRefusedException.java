package com.clinic.schedule.clinicalrecord.domain.exception;

/** What a copy of a record refuses, and why (D-95). A 409. */
public class RecordCopyRefusedException extends RuntimeException {

    /** Why it was refused. */
    public enum Reason {
        /** The patient already has a copy waiting to be authenticated. */
        ALREADY_WAITING,
        /** It was authenticated already: it changes no more. */
        ALREADY_AUTHENTICATED,
        /** It was put together again after the practitioner reviewed it: they have not seen this one. */
        REASSEMBLED_SINCE,
        /** The practitioner did not say they reviewed it. */
        NOT_REVIEWED,
        /** It is not authenticated yet. */
        NOT_AUTHENTICATED,
        /** It goes on paper, and the patient asked for a PDF — or the other way round. */
        OTHER_DELIVERY,
        /** It was handed over already. */
        ALREADY_HANDED_OVER,
        /** Reception did not say it saw the document of whoever collects it. */
        DOCUMENT_NOT_SEEN
    }

    private final Reason reason;

    public RecordCopyRefusedException(Reason reason) {
        super("The copy of the record was refused: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
