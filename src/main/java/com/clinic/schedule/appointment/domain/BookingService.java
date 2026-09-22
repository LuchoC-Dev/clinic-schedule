package com.clinic.schedule.appointment.domain;

import java.util.Collection;

/**
 * The way into the booking domain. Creating an appointment and checking it against the rest
 * of the agenda happen together here, so there is no path that produces an appointment
 * nobody validated: {@code Appointment.book} and {@code rescheduleTo} are reachable only
 * from this package.
 *
 * <p>It takes the agenda as a collection rather than reading it, which keeps the domain free
 * of ports. Fetching the relevant appointments, and the transaction around it, belong to the
 * use case.
 *
 * <p>Availability — the practitioner's weekly schedule, breaks and time off — is not checked
 * here: it belongs to the practitioner module, which does not exist yet.
 */
public final class BookingService {

    private final BookingPolicy policy = new BookingPolicy();

    /**
     * @param id     assigned by the caller, so a retried request reuses it instead of booking
     *               twice; {@link AppointmentId#newId()} when there is nothing to reuse
     * @param agenda appointments that could collide with the requested slot
     * @return the appointment, ready to be stored
     * @throws PractitionerAlreadyBookedException or {@link PatientAlreadyBookedException} when the slot is taken
     */
    public Appointment book(AppointmentId id, PatientId patientId, PractitionerId practitionerId, TimeSlot slot,
            String reason, String note, Collection<Appointment> agenda) {
        Appointment candidate = Appointment.book(id, patientId, practitionerId, slot, reason, note);
        policy.check(candidate, agenda);
        return candidate;
    }

    /**
     * Moves an appointment to another slot, checking the new one before handing it over, so a
     * rejected move leaves the original appointment untouched.
     *
     * <p>The appointment itself and cancelled ones are ignored when looking for collisions,
     * so a small move does not clash with the slot being left behind.
     *
     * @return the appointment on its new slot, which is what has to be stored
     */
    public Appointment reschedule(Appointment appointment, TimeSlot newSlot, Collection<Appointment> agenda) {
        Appointment moved = appointment.movedTo(newSlot);
        policy.check(moved, agenda);
        return moved;
    }
}
