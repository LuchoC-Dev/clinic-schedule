package com.clinic.schedule.appointment.domain;

/**
 * The patient already has an appointment overlapping the requested slot, with any
 * practitioner: a patient cannot be in two consulting rooms at the same time.
 */
public class PatientAlreadyBookedException extends BookingRuleViolationException {

    public PatientAlreadyBookedException(PatientId patientId, TimeSlot slot) {
        super("Patient %d already has an appointment overlapping %s - %s"
                .formatted(patientId.value(), slot.startsAt(), slot.endsAt()));
    }
}
