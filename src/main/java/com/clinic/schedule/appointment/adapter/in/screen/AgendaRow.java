package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * One line of an agenda screen: the appointment with its patient's and practitioner's names
 * instead of ids, and its state already put into words.
 */
record AgendaRow(
        UUID id,
        LocalDate date,
        LocalTime startsAt,
        LocalTime endsAt,
        UUID patientId,
        String patient,
        UUID practitionerId,
        String practitioner,
        String reason,
        String note,
        boolean booked,
        boolean arrived,
        boolean lateNotice,
        String state) {

    /** @param arrivedAt when the patient arrived, if they did — the queues say it (D-65) */
    static AgendaRow of(Appointment appointment, PatientSummary patient, String practitioner, LocalDateTime arrivedAt) {
        String patientLabel = patient == null ? "(paciente desconocido)"
                : patient.document() == null ? patient.fullName()
                : patient.fullName() + " — " + patient.document();
        return new AgendaRow(
                appointment.id().value(),
                appointment.slot().startsAt().toLocalDate(),
                appointment.slot().startsAt().toLocalTime(),
                appointment.slot().endsAt().toLocalTime(),
                appointment.patientId().value(),
                patientLabel,
                appointment.practitionerId().value(),
                practitioner == null ? "(profesional desconocido)" : practitioner,
                appointment.reason(),
                appointment.note(),
                appointment.isBooked(),
                arrivedAt != null,
                appointment.hasLateArrivalNotice(),
                stateOf(appointment, arrivedAt));
    }

    /** The day with its year, for lists that span more than a week. Public so the template can call it. */
    public String dayLabel() {
        return AgendaDay.labelWithYear(date);
    }

    private static String stateOf(Appointment appointment, LocalDateTime arrivedAt) {
        if (appointment.status() == AppointmentStatus.ABSENT) {
            return "Ausente";
        }
        if (!appointment.isBooked()) {
            return "Cancelado";
        }
        if (arrivedAt != null) {
            return "Llegó a las " + arrivedAt.toLocalTime().withSecond(0).withNano(0);
        }
        if (appointment.hasLateArrivalNotice()) {
            return "Avisó que llega tarde";
        }
        return "Reservado";
    }
}
