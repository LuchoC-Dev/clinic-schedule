package com.clinic.schedule.appointment.adapter.in.external;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.AppointmentSummary;
import com.clinic.schedule.appointment.api.PatientAppointment;
import com.clinic.schedule.appointment.application.usecase.FindPatientDayUseCase;
import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.usecase.FindAppointmentUseCase;
import com.clinic.schedule.appointment.application.usecase.FindAppointmentsBetweenUseCase;
import com.clinic.schedule.appointment.application.usecase.ViewPatientAppointmentsUseCase;
import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentId;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.PatientId;

/**
 * Answers this module's {@link AppointmentReader} for other modules. A driving adapter like the
 * REST controllers, only called by another module instead of over HTTP: it calls a use case and
 * translates the answer into the api's own DTO, so no other module sees the domain (D-48).
 *
 * <p>{@code adapter/in/external/} mirrors {@code adapter/out/external/}, which holds the calls
 * the other way round.
 */
@Component
class AppointmentReaderAdapter implements AppointmentReader {

    private final FindPatientDayUseCase patientDay;
    private final FindAppointmentUseCase appointment;
    private final FindAppointmentsBetweenUseCase between;
    private final ViewPatientAppointmentsUseCase patientAppointments;

    AppointmentReaderAdapter(FindPatientDayUseCase patientDay, FindAppointmentUseCase appointment,
            FindAppointmentsBetweenUseCase between, ViewPatientAppointmentsUseCase patientAppointments) {
        this.patientDay = patientDay;
        this.appointment = appointment;
        this.between = between;
        this.patientAppointments = patientAppointments;
    }

    @Override
    public List<AppointmentSummary> findNotCancelledBetween(LocalDate from, LocalDate to) {
        return between.handle(from, to).stream()
                .map(AppointmentReaderAdapter::summary)
                .toList();
    }

    @Override
    public Optional<AppointmentSummary> findBooked(UUID appointmentId) {
        try {
            Appointment found = appointment.handle(new AppointmentId(appointmentId));
            return found.isBooked() ? Optional.of(summary(found)) : Optional.empty();
        } catch (AppointmentNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<AppointmentSummary> findNotCancelledForPatientOn(UUID patientId, LocalDate date) {
        return patientDay.handle(new PatientId(patientId), date).stream()
                .map(AppointmentReaderAdapter::summary)
                .toList();
    }

    @Override
    public List<PatientAppointment> findForPatient(UUID patientId) {
        return patientAppointments.handle(new PatientId(patientId)).stream()
                .map(AppointmentReaderAdapter::forPatient)
                .toList();
    }

    /** Without the note and without who cancelled it, which are reception's (D-76, D-80). */
    private static PatientAppointment forPatient(Appointment appointment) {
        return new PatientAppointment(
                appointment.id().value(),
                appointment.practitionerId().value(),
                appointment.slot().startsAt(),
                appointment.reason(),
                PatientAppointment.State.valueOf(appointment.status().name()),
                appointment.cancellation()
                        .map(cancellation -> PatientAppointment.Requester.valueOf(cancellation.requestedBy().name()))
                        .orElse(null));
    }

    private static AppointmentSummary summary(Appointment appointment) {
        return new AppointmentSummary(
                appointment.id().value(),
                appointment.patientId().value(),
                appointment.practitionerId().value(),
                appointment.slot().startsAt(),
                appointment.status() == AppointmentStatus.ABSENT);
    }
}
