package com.clinic.schedule.arrival.session.screen;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.clinic.schedule.arrival.eventstore.StreamChangedException;
import com.clinic.schedule.arrival.session.command.AppointmentArrivalRefusedException;
import com.clinic.schedule.arrival.session.command.EntryNotFoundException;
import com.clinic.schedule.arrival.session.command.NoQueueServedException;
import com.clinic.schedule.arrival.session.command.QueueNotOpenException;
import com.clinic.schedule.arrival.session.command.UnknownPatientException;
import com.clinic.schedule.arrival.session.domain.exception.SessionRuleViolationException;
import com.clinic.schedule.cashdesk.api.ChargeRefusedException;

/** The refusals of a queue at work, as the staff reads them; the API says them in English (D-13). */
final class ScreenRefusals {

    private ScreenRefusals() {
    }

    /** What to say, or the exception again when it is not a refusal. */
    static String of(RuntimeException e) {
        return switch (e) {
            case SessionRuleViolationException rule -> switch (rule.reason()) {
                case QUEUE_ALREADY_OPEN -> "La cola ya está abierta.";
                case NOT_SERVING -> "No atendés esta cola.";
                case ALREADY_WAITING -> "Ese paciente ya está esperando en esta cola.";
                case ALREADY_ARRIVED -> "El paciente de ese turno ya llegó.";
                case ALREADY_KEPT_FOR_THEM -> "Ese paciente ya espera a ese profesional.";
                case NOT_KEPT -> "Ese paciente no espera a nadie en particular.";
                case ALREADY_EMERGENCY -> "Ese paciente ya es una urgencia.";
                case NOT_WAITING -> "Ese paciente ya no está esperando.";
                case NOBODY_WAITING -> "No hay nadie esperando.";
                case NOBODY_CALLED -> "No tenés ningún paciente llamado.";
                case STILL_CALLED -> "Terminá con el paciente llamado, o indicá que no ingresó, antes de llamar al siguiente.";
                case AUTO_CLOSE_ALREADY_OFF -> "La cola ya no se cierra sola.";
            };
            case AppointmentArrivalRefusedException refused -> switch (refused.reason()) {
                case NOT_BOOKED -> "Ese turno no está reservado: fue cancelado o el paciente estuvo ausente.";
                case NOT_TODAY -> "Con un turno se llega el día del turno.";
                case NO_QUEUE -> "El profesional de ese turno no atiende ninguna cola.";
            };
            case ChargeRefusedException refused -> switch (refused.reason()) {
                case CHARGE_REQUIRED -> "Falta el importe del cobro: sin cobro ni cobertura, solo entra una urgencia.";
                case INVALID_AMOUNT -> "El importe debe ser mayor que cero, con dos decimales como mucho.";
                case AMOUNT_TOO_HIGH -> "El importe no puede superar " + pesos(refused.ceiling()) + ".";
                case NOT_INSURED -> "El paciente no tiene obra social cargada: paga como particular.";
                case IDENTITY_NOT_VERIFIED -> "La identidad del paciente no está verificada: paga como particular.";
                case TOKEN_TOO_LONG -> "El token no puede superar los 50 caracteres.";
            };
            case QueueNotOpenException closed -> "La cola está cerrada: abrila primero.";
            case NoQueueServedException none -> "No atendés ninguna cola.";
            case EntryNotFoundException missing -> "Ese paciente no está en la cola.";
            case UnknownPatientException unknown -> "Ese paciente no existe.";
            case StreamChangedException changed -> "La cola cambió mientras tanto. Revisala y probá de nuevo.";
            default -> throw e;
        };
    }

    /** An amount as the staff reads it: $ 1.500,50. */
    private static String pesos(BigDecimal amount) {
        return new DecimalFormat("$ #,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")))
                .format(amount);
    }
}
