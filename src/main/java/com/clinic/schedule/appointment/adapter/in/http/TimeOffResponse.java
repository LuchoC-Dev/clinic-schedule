package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.appointment.application.usecase.RecordTimeOffUseCase;

/**
 * The absence as recorded, and what it did to the agenda: the appointments cancelled, and the
 * ones reception still has to handle because the patient is already there.
 */
record TimeOffResponse(
        UUID practitionerId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        List<AppointmentResponse> cancelled,
        List<AppointmentResponse> needsManualHandling) {

    static TimeOffResponse of(TimeOffRequest request, RecordTimeOffUseCase.Command command,
            RecordTimeOffUseCase.Result result) {
        return new TimeOffResponse(
                request.practitionerId(),
                command.span().startsAt(),
                command.span().endsAt(),
                result.cancelled().stream().map(AppointmentResponse::of).toList(),
                result.needsManualHandling().stream().map(AppointmentResponse::of).toList());
    }
}
