package com.clinic.schedule.portal.adapter.in.http;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinic.schedule.portal.application.usecase.ListOwnAppointmentsUseCase;
import com.clinic.schedule.portal.domain.Appointment;
import com.clinic.schedule.portal.domain.OwnAppointments;

/**
 * A patient's appointments as they read them (D-80): those ahead and those of the last 12 months. Never the
 * note nor who cancelled one: the types below have no place for them.
 */
record OwnAppointmentsResponse(List<Entry> upcoming, List<Entry> past, List<Member> family) {

    /** A member of the patient's family they act for, and their appointments, read as the patient's own (D-85). */
    record Member(UUID patientId, String fullName, List<Entry> upcoming, List<Entry> past) {
    }

    /** {@code cancellationRequestedBy} is null unless it was cancelled with its cancellation recorded. */
    record Entry(UUID id, LocalDateTime startsAt, Practitioner practitioner, String reason, Appointment.State state,
            Appointment.Requester cancellationRequestedBy) {
    }

    record Practitioner(UUID id, String fullName) {
    }

    static OwnAppointmentsResponse of(OwnAppointments appointments,
            List<ListOwnAppointmentsUseCase.MemberAppointments> family, Map<UUID, String> practitionerNames) {
        return new OwnAppointmentsResponse(entries(appointments.upcoming(), practitionerNames),
                entries(appointments.past(), practitionerNames),
                family.stream()
                        .map(member -> new Member(member.member().patientId(), member.member().fullName(),
                                entries(member.appointments().upcoming(), practitionerNames),
                                entries(member.appointments().past(), practitionerNames)))
                        .toList());
    }

    private static List<Entry> entries(List<Appointment> appointments, Map<UUID, String> practitionerNames) {
        return appointments.stream().map(appointment -> entry(appointment, practitionerNames)).toList();
    }

    private static Entry entry(Appointment appointment, Map<UUID, String> practitionerNames) {
        return new Entry(appointment.id(), appointment.startsAt(),
                new Practitioner(appointment.practitionerId(), practitionerNames.get(appointment.practitionerId())),
                appointment.reason(), appointment.state(), appointment.cancellationRequestedBy());
    }
}
