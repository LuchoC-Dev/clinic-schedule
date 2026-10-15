package com.clinic.schedule.clinicalrecord.application.exception;

/**
 * The user may not read this clinical record, or may not record a consultation of this patient
 * now. A 403, never an empty answer (D-50); the same for a patient who does not exist, so the
 * refusal does not tell whether they do (D-49).
 *
 * <p>Its {@link #reason()} tells the two apart, and names the refusal in the API's {@code code} (D-100), so the
 * staff's application says why a consultation was refused as the screens do (D-104).
 */
public class ClinicalRecordAccessDeniedException extends RuntimeException {

    /** Why it was refused. */
    public enum Reason {
        /** The user may not read the record at all. */
        NO_ACCESS,
        /** The practitioner may read it, but the patient has not arrived to be seen by them today. */
        NOT_ARRIVED
    }

    private final Reason reason;

    private ClinicalRecordAccessDeniedException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public static ClinicalRecordAccessDeniedException toRead() {
        return new ClinicalRecordAccessDeniedException("The user may not read this patient's clinical record",
                Reason.NO_ACCESS);
    }

    /** The practitioner may read the record, but the patient has not arrived to be seen by them today. */
    public static ClinicalRecordAccessDeniedException toRecord() {
        return new ClinicalRecordAccessDeniedException(
                "A consultation is recorded only for a patient who arrived to be seen by this practitioner today",
                Reason.NOT_ARRIVED);
    }

    public Reason reason() {
        return reason;
    }

    /** Whether it was only the recording that was refused: a screen can then say why. */
    public boolean mayRead() {
        return reason == Reason.NOT_ARRIVED;
    }
}
