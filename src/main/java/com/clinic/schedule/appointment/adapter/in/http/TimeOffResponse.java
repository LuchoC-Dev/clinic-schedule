package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.appointment.application.usecase.RecordTimeOffUseCase;

/**
 * The absence as recorded, and what it did to the agenda: the appointments cancelled, and the
 * ones reception still has to handle because the patient is already there. {@code patients} names
 * the patients of both by id, as the screen says them (D-108).
 */
record TimeOffResponse(
        UUID practitionerId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        List<AppointmentResponse> cancelled,
        List<AppointmentResponse> needsManualHandling,
        Map<UUID, String> patients) {

    static TimeOffResponse of(TimeOffRequest request, RecordTimeOffUseCase.Command command,
            RecordTimeOffUseCase.Result result, Map<UUID, String> patients) {
        return new TimeOffResponse(
                request.practitionerId(),
                command.span().startsAt(),
                command.span().endsAt(),
                result.cancelled().stream().map(appointment -> AppointmentResponse.of(appointment, null)).toList(),
                result.needsManualHandling().stream()
                        .map(appointment -> AppointmentResponse.of(appointment, result.arrivals().get(appointment.id())))
                        .toList(),
                patients);
    }
}
