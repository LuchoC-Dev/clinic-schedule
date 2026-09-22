package com.clinic.schedule.appointment.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * Where appointments are kept. The only external dependency this module has for now; the
 * practitioner's reasons and availability will be a second one.
 *
 * <p>The reads are per day rather than per exact range on purpose: a day holds a handful of
 * appointments, so fetching it whole and letting the domain do the precise overlap check
 * keeps the query trivial and portable — no stored end time to compare against. The daily
 * reads are also what the agenda view of a day will need.
 */
public interface AppointmentRepository {

    Optional<Appointment> findById(AppointmentId id);

    /** Booked appointments of that practitioner on that day. Cancelled ones are left out. */
    List<Appointment> findBookedForPractitionerOn(PractitionerId practitionerId, LocalDate date);

    /** Booked appointments of that patient on that day, with any practitioner. */
    List<Appointment> findBookedForPatientOn(PatientId patientId, LocalDate date);

    /** Stores a new appointment or the new state of an existing one. */
    void save(Appointment appointment);
}
