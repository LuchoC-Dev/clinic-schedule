package com.clinic.schedule.appointment.application.usecase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.clinic.schedule.appointment.application.port.AppointmentRepository;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;
import com.clinic.schedule.appointment.domain.PractitionerId;

/**
 * The port backed by a map, so the use cases can be tested without a database or Spring.
 *
 * <p>It behaves like the real adapter in the two ways that matter to these tests: reads
 * return only booked appointments, except a patient's history, and {@code save} replaces by id — which is what makes
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
    public List<Appointment> findNotCancelledForPatientOn(PatientId patientId, LocalDate date) {
        return findNotCancelledBetween(date, date).stream()
                .filter(appointment -> appointment.patientId().equals(patientId))
                .toList();
    }

    @Override
    public List<Appointment> findNotCancelledForPractitionerBetween(PractitionerId practitionerId, LocalDate from,
            LocalDate to) {
        return findNotCancelledBetween(from, to).stream()
                .filter(appointment -> appointment.practitionerId().equals(practitionerId))
                .toList();
    }

    @Override
    public List<Appointment> findNotCancelledBetween(LocalDate from, LocalDate to) {
        List<Appointment> found = new ArrayList<>();
        for (Appointment appointment : stored.values()) {
            LocalDate day = appointment.slot().startsAt().toLocalDate();
            if (appointment.status() != AppointmentStatus.CANCELLED && !day.isBefore(from) && !day.isAfter(to)) {
                found.add(appointment);
            }
        }
        return found;
    }

    @Override
    public List<Appointment> findAllForPatient(PatientId patientId) {
        return stored.values().stream()
                .filter(appointment -> appointment.patientId().equals(patientId))
                .toList();
    }

    @Override
    public List<Appointment> findBookedStartingBy(java.time.LocalDateTime instant) {
        List<Appointment> found = new ArrayList<>();
        for (Appointment appointment : stored.values()) {
            if (appointment.isBooked() && !appointment.slot().startsAt().isAfter(instant)) {
                found.add(appointment);
            }
        }
        return found;
    }

    @Override
    public List<Appointment> findCancelledBetween(PractitionerId practitioner, java.time.LocalDateTime from,
            java.time.LocalDateTime until, int limit) {
        return stored.values().stream()
                .filter(appointment -> appointment.cancellation().isPresent())
                .filter(appointment -> practitioner == null || appointment.practitionerId().equals(practitioner))
                .filter(appointment -> !appointment.cancellation().get().at().isBefore(from)
                        && appointment.cancellation().get().at().isBefore(until))
                .sorted(java.util.Comparator.comparing(
                        (Appointment appointment) -> appointment.cancellation().get().at()).reversed())
                .limit(limit)
                .toList();
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
