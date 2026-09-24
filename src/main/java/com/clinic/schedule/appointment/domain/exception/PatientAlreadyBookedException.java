package com.clinic.schedule.appointment.domain.exception;

import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.TimeSlot;

/**
 * The patient already has an appointment overlapping the requested slot, with any
 * practitioner: a patient cannot be in two consulting rooms at the same time.
 */
public class PatientAlreadyBookedException extends BookingRuleViolationException {

    public PatientAlreadyBookedException(PatientId patientId, TimeSlot slot) {
        super("Patient %s already has an appointment overlapping %s - %s"
                .formatted(patientId.value(), slot.startsAt(), slot.endsAt()));
    }
}
