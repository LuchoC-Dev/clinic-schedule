package com.clinic.schedule.patient;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.shared.web.ErrorResponse;

/**
 * This module's exceptions as HTTP answers, scoped to its controller so no shared class has
 * to import them (D-23).
 *
 * <p>The validation handling is the same code as in `appointment`. It is duplicated on
 * purpose for now: a second copy is cheap, and moving it to a shared advice is the kind of
 * thing worth doing once it is clear that every module wants the same shape — which the
 * third module will tell us.
 */
@RestControllerAdvice(assignableTypes = PatientController.class)
class PatientExceptionHandler {

    @ExceptionHandler(PatientNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(PatientNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", e.getMessage()));
    }

    @ExceptionHandler(DuplicateNationalIdException.class)
    ResponseEntity<ErrorResponse> duplicateNationalId(DuplicateNationalIdException e) {
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
