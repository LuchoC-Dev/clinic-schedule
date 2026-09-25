package com.clinic.schedule.clinicalrecord.application.exception;

import com.clinic.schedule.clinicalrecord.domain.ConsultationId;

/**
 * Someone else added to the consultation between reading it and saving it — two addenda at the
 * same time. Nothing was saved; sending it again works. A 409 (D-52).
 */
public class ConsultationChangedException extends RuntimeException {

    public ConsultationChangedException(ConsultationId id) {
        super("Consultation " + id.value() + " changed while this addendum was being added; send it again");
    }
}
