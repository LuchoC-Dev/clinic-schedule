package com.clinic.schedule.cashdesk.adapter.in.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.cashdesk.application.exception.VisitNotFoundException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * The cash desk's refusals, in the uniform error body (D-87): an unknown visit is 404, a visit charged
 * already 409, and an amount missing, wrong or above the ceiling 400.
 */
@RestControllerAdvice(assignableTypes = CashDeskController.class)
class CashDeskExceptionHandler {

    @ExceptionHandler(VisitNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(VisitNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e.getMessage()));
    }

    @ExceptionHandler(ChargeRuleViolationException.class)
    ResponseEntity<ErrorResponse> refused(ChargeRuleViolationException e) {
        if (e.reason() == ChargeRuleViolationException.Reason.ALREADY_CHARGED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e.getMessage()));
        }
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
