package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.BookingChannel;
import com.clinic.schedule.appointment.domain.CancellationOrigin;
import com.clinic.schedule.appointment.domain.CancellationRequester;

/**
 * An appointment as the API returns it: plain values, no domain types. The wrappers stay
 * inside, so the JSON does not grow a nested object per id and the contract does not move
 * when the domain does.
 *
 * <p>It carries {@code endsAt} even though the domain stores a duration, because every
 * client would compute it otherwise.
 *
 * <p>{@code cancellation} is null unless it is cancelled, and for one cancelled before anything was
 * recorded of it (D-76).
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
        AppointmentStatus status,
        LocalDateTime arrivedAt,
        boolean lateArrivalNotice,
        Cancellation cancellation,
        BookingChannel bookedVia) {

    /** {@code note} is null when reception wrote none. */
    record Cancellation(LocalDateTime at, String by, CancellationOrigin origin, CancellationRequester requestedBy,
            String note) {
    }

    static AppointmentResponse of(Appointment appointment, LocalDateTime arrivedAt) {
        return new AppointmentResponse(
                appointment.id().value(),
                appointment.patientId().value(),
                appointment.practitionerId().value(),
                appointment.slot().startsAt(),
                appointment.slot().endsAt(),
                (int) appointment.slot().duration().toMinutes(),
                appointment.reason(),
                appointment.note(),
                appointment.status(),
                arrivedAt,
                appointment.hasLateArrivalNotice(),
                appointment.cancellation()
                        .map(cancellation -> new Cancellation(cancellation.at(), cancellation.by(),
                                cancellation.origin(), cancellation.requestedBy(), cancellation.note()))
                        .orElse(null),
                appointment.bookedVia());
    }
}
