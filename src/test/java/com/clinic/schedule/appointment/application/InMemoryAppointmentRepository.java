package com.clinic.schedule.appointment.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * The port backed by a map, so the use cases can be tested without a database or Spring.
 *
 * <p>It behaves like the real adapter in the two ways that matter to these tests: reads
 * return only booked appointments, and {@code save} replaces by id — which is what makes
 * cancelling and rescheduling visible on the next read.
 */
class InMemoryAppointmentRepository implements AppointmentRepository {

    private final Map<AppointmentId, Appointment> stored = new LinkedHashMap<>();
    private int saves;

    @Override
    public Optional<Appointment> findById(AppointmentId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public List<Appointment> findBookedForPractitionerOn(PractitionerId practitionerId, LocalDate date) {
        List<Appointment> found = new ArrayList<>();
        for (Appointment appointment : stored.values()) {
            if (appointment.isBooked()
                    && appointment.practitionerId().equals(practitionerId)
                    && appointment.slot().startsAt().toLocalDate().equals(date)) {
                found.add(appointment);
            }
        }
        return found;
    }

    @Override
    public List<Appointment> findBookedForPatientOn(PatientId patientId, LocalDate date) {
        List<Appointment> found = new ArrayList<>();
        for (Appointment appointment : stored.values()) {
            if (appointment.isBooked()
                    && appointment.patientId().equals(patientId)
                    && appointment.slot().startsAt().toLocalDate().equals(date)) {
                found.add(appointment);
            }
        }
        return found;
    }

    @Override
    public List<Appointment> findBookedWithoutCheckInStartingBy(java.time.LocalDateTime instant) {
        List<Appointment> found = new ArrayList<>();
        for (Appointment appointment : stored.values()) {
            if (appointment.isBooked() && !appointment.isCheckedIn()
                    && !appointment.slot().startsAt().isAfter(instant)) {
                found.add(appointment);
            }
        }
        return found;
    }

    @Override
    public void save(Appointment appointment) {
        stored.put(appointment.id(), appointment);
        saves++;
    }

    /** Pre-loads appointments without going through a use case. */
    void given(Appointment... appointments) {
        for (Appointment appointment : appointments) {
            stored.put(appointment.id(), appointment);
        }
        saves = 0;
    }

    int saveCount() {
        return saves;
    }

    int storedCount() {
        return stored.size();
    }
}
