package com.clinic.schedule.appointment.adapter.in.external;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.appointment.api.AppointmentReader;
import com.clinic.schedule.appointment.api.AppointmentSummary;
import com.clinic.schedule.appointment.application.usecase.FindPatientDayUseCase;
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

    AppointmentReaderAdapter(FindPatientDayUseCase patientDay) {
        this.patientDay = patientDay;
    }

    @Override
    public List<AppointmentSummary> findNotCancelledForPatientOn(UUID patientId, LocalDate date) {
        return patientDay.handle(new PatientId(patientId), date).stream()
                .map(appointment -> new AppointmentSummary(
                        appointment.id().value(),
                        appointment.patientId().value(),
                        appointment.practitionerId().value(),
                        appointment.slot().startsAt(),
                        appointment.isCheckedIn(),
                        appointment.status() == AppointmentStatus.ABSENT))
                .toList();
    }
}
