package com.clinic.schedule.appointment.application;

import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * A practitioner asked for another practitioner's agenda. Refused rather than answered with
 * their own, so the caller learns the request was wrong instead of reading a different agenda
 * than the one it asked for (D-39).
 */
public class AgendaAccessDeniedException extends RuntimeException {

    public AgendaAccessDeniedException(PractitionerId asking, PractitionerId requested) {
        super("Practitioner %s may only read their own agenda, not %s's".formatted(asking.value(), requested.value()));
    }
}
