package com.clinic.schedule.appointment.domain.exception;

import com.clinic.schedule.appointment.domain.PractitionerId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/** The practitioner already has an appointment overlapping the requested slot. */
public class PractitionerAlreadyBookedException extends BookingRuleViolationException {

    public PractitionerAlreadyBookedException(PractitionerId practitionerId, TimeSlot slot) {
        super("Practitioner %s already has an appointment overlapping %s - %s"
                .formatted(practitionerId.value(), slot.startsAt(), slot.endsAt()));
    }
}
