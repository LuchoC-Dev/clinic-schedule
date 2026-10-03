package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.LocalDateTime;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.Cancellation;
import com.clinic.schedule.appointment.domain.CancellationRequester;

/** A cancellation put into the screens' words, in Spanish (D-13, D-76). */
final class CancellationWords {

    private CancellationWords() {
    }

    /**
     * What the patient's appointments say of a cancelled one: who cancelled it, when, how and who
     * asked; or that nothing was recorded, for one cancelled before anything was. Null when it is not
     * cancelled.
     */
    static String summary(Appointment appointment) {
        if (appointment.status() != AppointmentStatus.CANCELLED) {
            return null;
        }
        return appointment.cancellation()
                .map(cancellation -> switch (cancellation.origin()) {
                    case TIME_OFF -> "Por la ausencia del profesional, cargada por " + cancellation.by() + " el "
                            + when(cancellation.at());
                    case ONLINE -> "Por el paciente, en la web, el " + when(cancellation.at());
                    case EMAIL_LINK -> "Con el link del email, el " + when(cancellation.at()) + " · lo pidió "
                            + requester(cancellation.requestedBy());
                    case BY_HAND -> "Por " + cancellation.by() + " el " + when(cancellation.at()) + " · lo pidió "
                            + requester(cancellation.requestedBy());
                })
                .orElse("Sin registro de la cancelación: es anterior a que se registraran.");
    }

    static String origin(Cancellation cancellation) {
        return switch (cancellation.origin()) {
            case BY_HAND -> "A mano";
            case TIME_OFF -> "Ausencia del profesional";
            case ONLINE -> "Por la web";
            case EMAIL_LINK -> "Con el link del email";
        };
    }

    /**
     * Who cancelled it, as reception reads it: a user of the practice by their username, the patient's web
     * account as the patient, and an email's link as itself (D-83).
     */
    static String who(Cancellation cancellation) {
        return switch (cancellation.origin()) {
            case ONLINE -> "el paciente (web)";
            case EMAIL_LINK -> "el link del email";
            case BY_HAND, TIME_OFF -> cancellation.by();
        };
    }

    /** Who cancelled it, as the admins read it: nothing that tells who the patient is (D-76, D-83). */
    static String whoForAdmins(Cancellation cancellation) {
        return switch (cancellation.origin()) {
            case ONLINE, EMAIL_LINK -> "paciente (web)";
            case BY_HAND, TIME_OFF -> cancellation.by();
        };
    }

    static String requester(CancellationRequester requester) {
        return switch (requester) {
            case PATIENT -> "el paciente";
            case PRACTICE -> "el consultorio";
        };
    }

    static String when(LocalDateTime at) {
        return AgendaDay.labelWithYear(at.toLocalDate()) + " " + at.toLocalTime().withSecond(0).withNano(0);
    }
}
