package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.Appointment;
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
        boolean checkedIn,
        boolean lateNotice,
        String state) {

    static AgendaRow of(Appointment appointment, PatientSummary patient, String practitioner) {
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
                appointment.isCheckedIn(),
                appointment.isAutoCancellationSuspended(),
                stateOf(appointment));
    }

    /** The day with its year, for lists that span more than a week. Public so the template can call it. */
    public String dayLabel() {
        return AgendaDay.labelWithYear(date);
    }

    private static String stateOf(Appointment appointment) {
        if (!appointment.isBooked()) {
            return "Cancelado";
        }
        if (appointment.isCheckedIn()) {
            return "Llegó a las " + appointment.checkedInAt().toLocalTime().withSecond(0).withNano(0);
        }
        if (appointment.isAutoCancellationSuspended()) {
            return "Avisó que llega tarde";
        }
        return "Reservado";
    }
}
