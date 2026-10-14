package com.clinic.schedule.appointment.adapter.in.http;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.BookingChannel;

/**
 * A day's agenda as reception's application shows it (D-98): the day, whose agenda it is — null for
 * everyone's —, the practitioners to choose from, and each appointment not cancelled, in order, with the names
 * of its patient and practitioner, which {@link AppointmentResponse} leaves to the client.
 */
record AgendaResponse(LocalDate date, UUID practitionerId, List<Practitioner> practitioners,
        List<Appointment> appointments) {

    record Practitioner(UUID id, String fullName) {
    }

    /**
     * @param patient the patient's name, or null when the patient module no longer knows them
     * @param patientDocument their document, as "DNI 30111222", or null when they have none
     * @param note reception's note, or null
     * @param arrivedAt when the patient arrived, if they did — the queues say it (D-65)
     * @param movedOnWeb the patient moved it on the web at some time (D-93)
     */
    record Appointment(UUID id, LocalDateTime startsAt, LocalDateTime endsAt, int durationMinutes, UUID patientId,
            String patient, String patientDocument, UUID practitionerId, String practitioner, String reason,
            String note, AppointmentStatus status, LocalDateTime arrivedAt, boolean lateArrivalNotice,
            BookingChannel bookedVia, boolean movedOnWeb) {
    }
}
