package com.clinic.schedule.cashdesk.adapter.in.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.cashdesk.application.exception.VisitNotFoundException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.shared.http.ErrorResponse;

/**
 * The cash desk's refusals, in the uniform error body (D-87, D-88, D-90): an unknown visit is 404; what the
 * visit's charges and coverage as they stand refuse — charged or covered already, nothing or not that much left
 * to give back, changed meanwhile, no coverage to withdraw or a copay left — 409; and an amount, a reason or a
 * token missing or wrong, or a patient who cannot go in covered, 400.
 */
@RestControllerAdvice(assignableTypes = CashDeskController.class)
class CashDeskExceptionHandler {

    @ExceptionHandler(VisitNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(VisitNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "Not found", e));
    }

    @ExceptionHandler(ChargeRuleViolationException.class)
    ResponseEntity<ErrorResponse> refused(ChargeRuleViolationException e) {
        return switch (e.reason()) {
            case ALREADY_CHARGED, NOTHING_TO_REFUND, REFUND_TOO_HIGH, CHANGED_MEANWHILE, ALREADY_COVERED, NOT_COVERED,
                    COPAY_LEFT ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "Conflict", e));
            case CHARGE_REQUIRED, INVALID_AMOUNT, AMOUNT_TOO_HIGH, REASON_REQUIRED, REASON_TOO_LONG, NOT_INSURED,
                    IDENTITY_NOT_VERIFIED, TOKEN_TOO_LONG ->
                    ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e));
        };
    }
}
