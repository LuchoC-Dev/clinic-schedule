package com.clinic.schedule.appointment.web;

import java.time.LocalTime;
import java.util.UUID;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.patient.api.PatientSummary;

/**
 * One line of the agenda screen: the appointment with its patient's name instead of an id,
 * and its state already put into words.
 */
record AgendaRow(
        UUID id,
        LocalTime startsAt,
        LocalTime endsAt,
        String patient,
        String reason,
        String note,
        boolean checkedIn,
        boolean lateNotice,
        String state) {

    static AgendaRow of(Appointment appointment, PatientSummary patient) {
        String patientLabel = patient == null ? "(paciente desconocido)"
                : patient.document() == null ? patient.fullName()
                : patient.fullName() + " — " + patient.document();
        return new AgendaRow(
                appointment.id().value(),
                appointment.slot().startsAt().toLocalTime(),
                appointment.slot().endsAt().toLocalTime(),
                patientLabel,
                appointment.reason(),
                appointment.note(),
                appointment.isCheckedIn(),
                appointment.isAutoCancellationSuspended(),
                stateOf(appointment));
    }

    private static String stateOf(Appointment appointment) {
        if (appointment.isCheckedIn()) {
            return "Llegó a las " + appointment.checkedInAt().toLocalTime().withSecond(0).withNano(0);
        }
        if (appointment.isAutoCancellationSuspended()) {
            return "Avisó que llega tarde";
        }
        return "Reservado";
    }
}
