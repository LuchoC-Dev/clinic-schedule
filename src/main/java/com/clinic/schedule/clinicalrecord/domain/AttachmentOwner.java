package com.clinic.schedule.clinicalrecord.domain;

/**
 * What an attachment belongs to (D-57): a consultation, or a check. An attachment of the record
 * as a whole — a report the patient brings from elsewhere — will be a third case, later.
 */
public sealed interface AttachmentOwner permits AttachmentOwner.OfConsultation, AttachmentOwner.OfCheck {

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
}
