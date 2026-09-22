package com.clinic.schedule.appointment.web;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentStatus;

/**
 * An appointment as the API returns it: plain values, no domain types. The wrappers stay
 * inside, so the JSON does not grow a nested object per id and the contract does not move
 * when the domain does.
 *
 * <p>It carries {@code endsAt} even though the domain stores a duration, because every
 * client would compute it otherwise.
 */
record AppointmentResponse(
        UUID id,
        UUID patientId,
        UUID practitionerId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        int durationMinutes,
        String reason,
        String note,
        AppointmentStatus status) {

    static AppointmentResponse of(Appointment appointment) {
        return new AppointmentResponse(
                appointment.id().value(),
                appointment.patientId().value(),
                appointment.practitionerId().value(),
                appointment.slot().startsAt(),
                appointment.slot().endsAt(),
                (int) appointment.slot().duration().toMinutes(),
                appointment.reason(),
                appointment.note(),
                appointment.status());
    }
}
