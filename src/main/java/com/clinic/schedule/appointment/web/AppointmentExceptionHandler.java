package com.clinic.schedule.appointment.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.appointment.application.AppointmentNotFoundException;
import com.clinic.schedule.appointment.domain.BookingRuleViolationException;
import com.clinic.schedule.shared.web.ErrorResponse;

/**
 * Turns this module's exceptions into HTTP answers. Scoped to the appointment controller
 * rather than global, so the module keeps its own translation and no shared class has to
 * import its exceptions (D-05).
 *
 * <p>A booking rule that says no is a 409: the request was well formed and the client can
 * retry with another time. Validation is a 400 and lists the offending fields.
 *
 * <p>When a second module gets endpoints, the validation part is what moves to a shared
 * advice; the rules stay here.
 */
@RestControllerAdvice(assignableTypes = AppointmentController.class)
class AppointmentExceptionHandler {

    @ExceptionHandler(AppointmentNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(AppointmentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", e.getMessage()));
    }

    @ExceptionHandler(BookingRuleViolationException.class)
    ResponseEntity<ErrorResponse> bookingRule(BookingRuleViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.ofValidation("The request contains invalid fields", fields));
    }
}
