package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.OwnAppointments;
import com.clinic.schedule.portal.domain.WebCancellationPolicy;
import com.clinic.schedule.portal.domain.WebMovePolicy;

/**
 * A patient's appointments as they read them (D-80): those ahead and those of the last 12 months. Never the
 * note nor who cancelled one: the types below have no place for them. Each says whether it may still be cancelled
 * (D-83) and moved (D-93) on the web, so the patients' application offers only what the rules allow (D-111).
 */
record OwnAppointmentsResponse(List<Entry> upcoming, List<Entry> past, List<Member> family) {

    /** A member of the patient's family they act for, and their appointments, read as the patient's own (D-85). */
    record Member(UUID patientId, String fullName, List<Entry> upcoming, List<Entry> past) {
    }

    /** {@code cancellationRequestedBy} is null unless it was cancelled with its cancellation recorded. */
    record Entry(UUID id, LocalDateTime startsAt, Practitioner practitioner, String reason, Appointment.State state,
            Appointment.Requester cancellationRequestedBy, boolean cancellable, boolean movable) {
    }

    record Practitioner(UUID id, String fullName) {
    }

    static OwnAppointmentsResponse of(OwnAppointments appointments,
            List<ListOwnAppointmentsUseCase.MemberAppointments> family, Map<UUID, String> practitionerNames,
            LocalDateTime now, int hoursBefore) {
        Entries entries = new Entries(practitionerNames, now, hoursBefore);
        return new OwnAppointmentsResponse(entries.of(appointments.upcoming()), entries.of(appointments.past()),
                family.stream()
                        .map(member -> new Member(member.member().patientId(), member.member().fullName(),
                                entries.of(member.appointments().upcoming()), entries.of(member.appointments().past())))
                        .toList());
    }

    private record Entries(Map<UUID, String> practitionerNames, LocalDateTime now, int hoursBefore) {

        List<Entry> of(List<Appointment> appointments) {
            return appointments.stream().map(this::entry).toList();
        }

        private Entry entry(Appointment appointment) {
            return new Entry(appointment.id(), appointment.startsAt(),
                    new Practitioner(appointment.practitionerId(),
                            practitionerNames.get(appointment.practitionerId())),
                    appointment.reason(), appointment.state(), appointment.cancellationRequestedBy(),
                    WebCancellationPolicy.canCancel(appointment, now, hoursBefore),
                    WebMovePolicy.canMove(appointment, now, hoursBefore));
        }
    }
}
