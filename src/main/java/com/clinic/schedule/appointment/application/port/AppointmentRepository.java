package com.clinic.schedule.appointment.application.port;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * Where appointments are kept.
 *
 * <p>The reads are per day rather than per exact range on purpose: a day holds a handful of
 * appointments, so fetching it whole and letting the domain do the precise overlap check
 * keeps the query trivial and portable — no stored end time to compare against. The agenda
 * views read whole days too, a span of them, for the same reason.
 */
public interface AppointmentRepository {

    Optional<Appointment> findById(AppointmentId id);

    /** Booked appointments of that practitioner on that day. Cancelled ones are left out. */
    List<Appointment> findBookedForPractitionerOn(PractitionerId practitionerId, LocalDate date);

    /** Booked appointments of that patient on that day, with any practitioner. */
    List<Appointment> findBookedForPatientOn(PatientId patientId, LocalDate date);

    /**
     * Appointments of that patient on that day, with any practitioner, booked or absent: every one
     * that was not called off (D-61).
     */
    List<Appointment> findNotCancelledForPatientOn(PatientId patientId, LocalDate date);

    /**
     * Appointments of that practitioner from {@code from} to {@code to}, both days included,
     * booked or absent: what an agenda shows (D-61).
     */
    List<Appointment> findNotCancelledForPractitionerBetween(PractitionerId practitionerId, LocalDate from, LocalDate to);

    /** The same, of every practitioner. */
    List<Appointment> findNotCancelledBetween(LocalDate from, LocalDate to);

    /** Every appointment of that patient, cancelled ones included: their history. */
    List<Appointment> findAllForPatient(PatientId patientId);

    /**
     * Booked appointments whose patient has not checked in and that started at or before
     * {@code instant}: the candidates to be marked absent. Which of them are actually
     * due is the appointment's decision, not the query's (D-31).
     */
    List<Appointment> findBookedWithoutCheckInStartingBy(LocalDateTime instant);

    /** Stores a new appointment or the new state of an existing one. */
    void save(Appointment appointment);
}
