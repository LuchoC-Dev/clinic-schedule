package com.clinic.schedule.practitioner;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.shared.web.ErrorResponse;

/**
 * This module's exceptions as HTTP answers, scoped to its controller so no shared class has
 * to import them (D-23). Validation errors are handled once for every module, in
 * {@code shared/web}.
 */
@RestControllerAdvice(assignableTypes = PractitionerController.class)
class PractitionerExceptionHandler {

    @ExceptionHandler({PractitionerNotFoundException.class, ReasonNotFoundException.class})
    ResponseEntity<ErrorResponse> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", e.getMessage()));
    }

    @ExceptionHandler(DuplicateReasonException.class)
    ResponseEntity<ErrorResponse> duplicateReason(DuplicateReasonException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    /** A week that does not make sense, or hours that end before they start. */
    @ExceptionHandler({InvalidWeeklyScheduleException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> malformedRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
