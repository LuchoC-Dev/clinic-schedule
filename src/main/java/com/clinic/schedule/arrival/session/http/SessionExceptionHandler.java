package com.clinic.schedule.arrival.session.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.arrival.eventstore.StreamChangedException;
import com.clinic.schedule.arrival.session.command.AppointmentArrivalRefusedException;
import com.clinic.schedule.arrival.session.command.EntryNotFoundException;
import com.clinic.schedule.arrival.session.command.NoQueueServedException;
import com.clinic.schedule.arrival.session.command.QueueNotOpenException;
import com.clinic.schedule.arrival.session.command.UnknownPatientException;
import com.clinic.schedule.arrival.session.domain.exception.SessionRuleViolationException;
import com.clinic.schedule.arrival.setup.QueueNotFoundException;
import com.clinic.schedule.cashdesk.api.ChargeRefusedException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * The refusals of a queue at work, in the uniform error body: an unknown queue or entry is 404, a
 * rule broken, a closed queue, a practitioner with no queue or a change made meanwhile is 409, and
 * an unknown patient or a charge refused is 400 (D-87).
 */
@RestControllerAdvice(assignableTypes = {SessionController.class, MyQueueController.class})
class SessionExceptionHandler {

    @ExceptionHandler({QueueNotFoundException.class, EntryNotFoundException.class})
    ResponseEntity<ErrorResponse> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e.getMessage()));
    }

    @ExceptionHandler({SessionRuleViolationException.class, QueueNotOpenException.class, NoQueueServedException.class,
            AppointmentArrivalRefusedException.class, StreamChangedException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    @ExceptionHandler({UnknownPatientException.class, ChargeRefusedException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
