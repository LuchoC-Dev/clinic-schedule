package com.clinic.schedule.appointment.adapter.in.screen;

import java.time.LocalDateTime;

import com.clinic.schedule.appointment.domain.Appointment;
import com.clinic.schedule.appointment.domain.AppointmentStatus;
import com.clinic.schedule.appointment.domain.Cancellation;
import com.clinic.schedule.appointment.domain.CancellationOrigin;
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
                .map(cancellation -> cancellation.origin() == CancellationOrigin.TIME_OFF
                        ? "Por la ausencia del profesional, cargada por " + cancellation.by() + " el "
                                + when(cancellation.at())
                        : "Por " + cancellation.by() + " el " + when(cancellation.at()) + " · lo pidió "
                                + requester(cancellation.requestedBy()))
                .orElse("Sin registro de la cancelación: es anterior a que se registraran.");
    }

    static String origin(Cancellation cancellation) {
        return switch (cancellation.origin()) {
            case BY_HAND -> "A mano";
            case TIME_OFF -> "Ausencia del profesional";
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
