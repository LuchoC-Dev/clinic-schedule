package com.clinic.schedule.appointment.web;

import com.clinic.schedule.appointment.application.AppointmentNotFoundException;
import com.clinic.schedule.appointment.application.OutsideWorkingHoursException;
import com.clinic.schedule.appointment.application.UnknownPatientException;
import com.clinic.schedule.appointment.application.UnknownPractitionerException;
import com.clinic.schedule.appointment.application.UnknownReasonException;
import com.clinic.schedule.appointment.domain.BookingRuleViolationException;
import com.clinic.schedule.appointment.domain.NotTheAppointmentDayException;
import com.clinic.schedule.appointment.domain.PatientAlreadyBookedException;
import com.clinic.schedule.appointment.domain.PractitionerAlreadyBookedException;
import com.clinic.schedule.appointment.domain.SlotInThePastException;
import com.clinic.schedule.appointment.domain.SlotTooFarAheadException;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException;

/**
 * What the screens tell reception when an operation is refused. The API answers in English,
 * and turning that into something a receptionist reads is the interface's job (D-13). This is
 * that job for the Thymeleaf screens; the single-page applications will do their own.
 */
final class ScreenMessages {

    private ScreenMessages() {
    }

    /** The exceptions an operation on the agenda may end with; anything else is a real failure. */
    static boolean isExpected(RuntimeException e) {
        return e instanceof BookingRuleViolationException
                || e instanceof AppointmentNotFoundException
                || e instanceof UnknownPatientException
                || e instanceof UnknownPractitionerException
                || e instanceof UnknownReasonException
                || e instanceof IllegalArgumentException;
    }

    static String of(RuntimeException e) {
        if (e instanceof PractitionerAlreadyBookedException) {
            return "El profesional ya tiene un turno en ese horario.";
        }
        if (e instanceof PatientAlreadyBookedException) {
            return "El paciente ya tiene otro turno en ese horario.";
        }
        if (e instanceof SlotInThePastException) {
            return "Ese horario ya pasó.";
        }
        if (e instanceof SlotTooFarAheadException) {
            return "No se reservan turnos con más de un año de anticipación: revisá el año.";
        }
        if (e instanceof NotTheAppointmentDayException) {
            return "Eso solo se puede hacer el día del turno.";
        }
        if (e instanceof OutsideWorkingHoursException) {
            return "El profesional no atiende en ese horario.";
        }
        if (e instanceof BookingRuleViolationException) {
            return "No se puede: el turno ya fue cancelado, o el paciente ya había llegado o avisado su demora.";
        }
        if (e instanceof AppointmentNotFoundException) {
            return "El turno no existe.";
        }
        if (e instanceof UnknownPatientException) {
            return "El paciente no está registrado.";
        }
        if (e instanceof UnknownPractitionerException) {
            return "El profesional no existe.";
        }
        if (e instanceof UnknownReasonException) {
            return "Esa razón no corresponde al profesional.";
        }
        return "Los datos ingresados no son válidos.";
    }

    /** A new patient typed on the booking screen could not be registered (D-37). */
    static String of(PatientRegistrationRefusedException e) {
        return switch (e.reason()) {
            case DUPLICATE_DOCUMENT -> "Ese documento ya pertenece a otro paciente: buscalo arriba y elegilo.";
            case INCOMPLETE_DOCUMENT -> "El documento del paciente nuevo necesita tipo y número, o ninguno de los dos.";
            case INVALID_DATA -> "Revisá el paciente nuevo: el nombre lleva letras y hasta 150 caracteres; el DNI, 7 u 8 dígitos; LE y LC, de 6 a 8 dígitos; CI y pasaporte, solo letras y números.";
        };
    }
}
