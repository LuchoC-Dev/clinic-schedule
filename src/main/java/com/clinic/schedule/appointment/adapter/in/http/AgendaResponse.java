package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.BookingChannel;
import com.clinic.schedule.appointment.domain.CancellationOrigin;
import com.clinic.schedule.appointment.domain.CancellationRequester;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * Reception's agenda as its application shows it (D-98): the day asked for, the days it spans — that day alone,
 * or its week from Monday to Sunday (D-100) —, whose agenda it is — null for everyone's —, the practitioners to
 * choose from, and each appointment not cancelled, in order, with the names of its patient and practitioner,
 * which {@link AppointmentResponse} leaves to the client.
 */
record AgendaResponse(LocalDate date, LocalDate from, LocalDate to, UUID practitionerId,
        List<Practitioner> practitioners, List<Appointment> appointments) {

    record Practitioner(UUID id, String fullName) {
    }

    /**
     * One appointment, with the names a screen shows; also a patient's appointments, cancelled ones included.
     *
     * @param patient the patient's name, or null when the patient module no longer knows them
     * @param patientDocument their document, as "DNI 30111222", or null when they have none
     * @param note reception's note, or null
     * @param arrivedAt when the patient arrived, if they did — the queues say it (D-65)
     * @param movedOnWeb the patient moved it on the web at some time (D-93)
     * @param cancellation who cancelled it, when, how and who asked (D-76); null unless it is cancelled
     */
    record Appointment(UUID id, LocalDateTime startsAt, LocalDateTime endsAt, int durationMinutes, UUID patientId,
            String patient, String patientDocument, UUID practitionerId, String practitioner, String reason,
            String note, AppointmentStatus status, LocalDateTime arrivedAt, boolean lateArrivalNotice,
            BookingChannel bookedVia, boolean movedOnWeb, Cancellation cancellation) {

        static Appointment of(com.clinic.schedule.appointment.domain.Appointment appointment, PatientSummary patient,
                String practitioner, LocalDateTime arrivedAt) {
            return new Appointment(
                    appointment.id().value(),
                    appointment.slot().startsAt(),
                    appointment.slot().endsAt(),
                    (int) appointment.slot().duration().toMinutes(),
                    appointment.patientId().value(),
                    patient == null ? null : patient.fullName(),
                    patient == null ? null : patient.document(),
                    appointment.practitionerId().value(),
                    practitioner,
                    appointment.reason(),
                    appointment.note(),
                    appointment.status(),
                    arrivedAt,
                    appointment.hasLateArrivalNotice(),
                    appointment.bookedVia(),
                    appointment.movedOnWeb(),
                    appointment.cancellation()
                            .map(cancellation -> new Cancellation(cancellation.at(), cancellation.by(),
                                    cancellation.origin(), cancellation.requestedBy(), cancellation.note()))
                            .orElse(null));
        }
    }

    /** {@code at} and {@code by} null for one cancelled before anything was recorded of it (D-76). */
    record Cancellation(LocalDateTime at, String by, CancellationOrigin origin, CancellationRequester requestedBy,
            String note) {
    }
}
