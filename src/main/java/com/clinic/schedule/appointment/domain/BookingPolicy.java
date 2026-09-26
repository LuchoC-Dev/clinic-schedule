package com.clinic.schedule.appointment.domain;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Collection;

import com.clinic.schedule.appointment.domain.exception.PatientAlreadyBookedException;
import com.clinic.schedule.appointment.domain.exception.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.exception.SlotInThePastException;
import com.clinic.schedule.appointment.domain.exception.SlotTooFarAheadException;

/**
 * When an appointment may be booked, and the two collision rules of the agenda.
 *
 * <p>An appointment starts from now on, and no more than a year ahead: an earlier start is a
 * mistake the sweep would mark absent within the minute, and a later one is almost
 * certainly a mistyped year (D-38).
 *
 * <p>The collision rules:
 *
 * <ul>
 *   <li>a practitioner cannot have two overlapping appointments;</li>
 *   <li>a patient cannot have two overlapping appointments, not even with different
 *       practitioners.</li>
 * </ul>
 *
 * <p>This is a domain service and not a method of {@link Appointment} because deciding it
 * needs the rest of the agenda, which no single appointment can know. It is reached only
 * through {@link BookingService}, so there is no way to book without being checked.
 *
 * <p>It receives the appointments to check against instead of fetching them, so it stays a
 * pure function: the caller decides how narrow that set is, and the rules can be tested
 * without a database.
 *
 * <p>Availability — the weekly schedule, breaks and time off — is a separate matter and is
 * not checked here: it belongs to the practitioner module, which does not exist yet.
 */
final class BookingPolicy {

    /** The furthest ahead an appointment may start. */
    static final Period BOOKING_HORIZON = Period.ofYears(1);

    /**
     * @throws SlotInThePastException    when the slot starts before {@code now}
     * @throws SlotTooFarAheadException  when it starts more than {@link #BOOKING_HORIZON} ahead
     */
    void checkWhen(TimeSlot slot, LocalDateTime now) {
        if (slot.startsAt().isBefore(now)) {
            throw new SlotInThePastException(slot);
        }
        if (slot.startsAt().isAfter(now.plus(BOOKING_HORIZON))) {
            throw new SlotTooFarAheadException(slot);
        }
    }

    /**
     * @param candidate the appointment being booked or rescheduled
     * @param agenda    existing appointments that could collide; cancelled ones and the
     *                  candidate itself are ignored, so rescheduling does not clash with
     *                  its own previous slot
     * @throws PractitionerAlreadyBookedException when the practitioner is taken
     * @throws PatientAlreadyBookedException      when the patient is taken
     */
    void check(Appointment candidate, Collection<Appointment> agenda) {
        for (Appointment existing : agenda) {
            if (!existing.isBooked() || candidate.isSameAs(existing)) {
                continue;
            }
            if (!candidate.slot().overlaps(existing.slot())) {
                continue;
            }
            if (candidate.practitionerId().equals(existing.practitionerId())) {
                throw new PractitionerAlreadyBookedException(candidate.practitionerId(), candidate.slot());
            }
            if (candidate.patientId().equals(existing.patientId())) {
                throw new PatientAlreadyBookedException(candidate.patientId(), candidate.slot());
            }
        }
    }
}
