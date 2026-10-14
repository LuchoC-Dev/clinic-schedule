package com.clinic.schedule.arrival.setup.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.arrival.eventstore.StreamChangedException;
import com.clinic.schedule.arrival.setup.QueueNotFoundException;
import com.clinic.schedule.arrival.setup.command.UnknownPractitionerException;
import com.clinic.schedule.arrival.setup.domain.exception.QueueRuleViolationException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * The queue endpoints' refusals, in the uniform error body: an unknown queue is 404, a broken rule
 * or a change made meanwhile is 409, and data that does not make sense is 400.
 */
@RestControllerAdvice(assignableTypes = QueueController.class)
class QueueExceptionHandler {

    @ExceptionHandler(QueueNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(QueueNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e));
    }

    @ExceptionHandler({QueueRuleViolationException.class, StreamChangedException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e));
    }

    @ExceptionHandler({UnknownPractitionerException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e));
    }
}
