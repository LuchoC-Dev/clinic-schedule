package com.clinic.schedule.appointment.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about appointments. The only way into this module besides its
 * endpoints and screens (D-24), and read-only, hence the name (D-18).
 *
 * <p>It answers facts, not what they mean to the caller: whether a checked-in appointment lets
 * a practitioner record a consultation is the clinical record module's rule, not this one's (D-48).
 */
public interface AppointmentReader {

    /**
     * The patient's appointments on that day, with any practitioner, booked or absent. Cancelled
     * ones are left out (D-61).
     */
    List<AppointmentSummary> findNotCancelledForPatientOn(UUID patientId, LocalDate date);

    /**
     * The appointments from one day to another, both included, of every practitioner, booked or absent:
     * every one not called off (D-61, D-78).
     */
    List<AppointmentSummary> findNotCancelledBetween(LocalDate from, LocalDate to);

    /** Every appointment of the patient, cancelled and absent ones included, in no order (D-80). */
    List<PatientAppointment> findForPatient(UUID patientId);

    /**
     * The start times worth offering for that reason with that practitioner on that day: every quarter of an
     * hour they work and a slot of its length is free, none already gone (D-33). None for a reason they do
     * not have.
     */
    List<LocalTime> suggestedTimes(UUID practitionerId, UUID reasonId, LocalDate date);

    /** The appointment, if it is still booked: neither cancelled nor absent (D-64). */
    Optional<AppointmentSummary> findBooked(UUID appointmentId);
}
