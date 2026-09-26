package com.clinic.schedule.appointment.adapter.in.external;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.AppointmentSummary;
import com.clinic.schedule.appointment.application.usecase.FindPatientDayUseCase;
import com.clinic.schedule.appointment.application.exception.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.usecase.FindAppointmentUseCase;
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

    AppointmentReaderAdapter(FindPatientDayUseCase patientDay, FindAppointmentUseCase appointment) {
        this.patientDay = patientDay;
        this.appointment = appointment;
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

    private static AppointmentSummary summary(Appointment appointment) {
        return new AppointmentSummary(
                appointment.id().value(),
                appointment.patientId().value(),
                appointment.practitionerId().value(),
                appointment.slot().startsAt(),
                appointment.isCheckedIn(),
                appointment.status() == AppointmentStatus.ABSENT);
    }
}
