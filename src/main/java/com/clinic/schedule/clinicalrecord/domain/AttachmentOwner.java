package com.clinic.schedule.clinicalrecord.domain;

/**
 * What an attachment belongs to (D-57): a consultation, a check, or the patient's record as a whole —
 * a record kept on paper before the application, a report never uploaded (D-94).
 */
public sealed interface AttachmentOwner
        permits AttachmentOwner.OfConsultation, AttachmentOwner.OfCheck, AttachmentOwner.OfRecord {

    /** A photo taken during a consultation, say. */
    record OfConsultation(ConsultationId consultation) implements AttachmentOwner {

        public OfConsultation {
            if (consultation == null) {
                throw new IllegalArgumentException("An attachment of a consultation names it");
            }
        }
    }

    /** An electrocardiogram trace, a laboratory report. */
    record OfCheck(CheckId check) implements AttachmentOwner {

        public OfCheck {
            if (check == null) {
                throw new IllegalArgumentException("An attachment of a check names it");
            }
        }
    }

    /** A scanned record on paper, a report from elsewhere: of the patient's record, and nothing in it. */
    record OfRecord(PatientId patient) implements AttachmentOwner {

        public OfRecord {
            if (patient == null) {
                throw new IllegalArgumentException("An attachment of a record names whose record it is");
            }
        }
    }
}
