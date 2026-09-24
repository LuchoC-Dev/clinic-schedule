package com.clinic.schedule.consultation.application.exception;

/**
 * The user may not read this clinical record, or may not record a consultation of this patient
 * now. A 403, never an empty answer (D-50); the same for a patient who does not exist, so the
 * refusal does not tell whether they do (D-49).
 */
public class ClinicalRecordAccessDeniedException extends RuntimeException {

    private final boolean mayRead;

    private ClinicalRecordAccessDeniedException(String message, boolean mayRead) {
        super(message);
        this.mayRead = mayRead;
    }

    public static ClinicalRecordAccessDeniedException toRead() {
        return new ClinicalRecordAccessDeniedException("The user may not read this patient's clinical record", false);
    }

    /** The practitioner may read the record, but the patient has not arrived to be seen by them today. */
    public static ClinicalRecordAccessDeniedException toRecord() {
        return new ClinicalRecordAccessDeniedException(
                "A consultation is recorded only for a patient who arrived to be seen by this practitioner today", true);
    }

    /** Whether it was only the recording that was refused: a screen can then say why. */
    public boolean mayRead() {
        return mayRead;
    }
}
