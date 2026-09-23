package com.clinic.schedule.shared.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bean Validation failures, answered the same way for every module: a 400 listing each
 * offending field with its message.
 *
 * <p>Global, unlike the modules' own advices. It imports nothing from any module — only
 * Spring's exception — so sharing it does not couple them (D-23). It was duplicated in the
 * first two modules and moved here with the third, once it was clear every module wanted
 * the same shape.
 */
@RestControllerAdvice
class ValidationExceptionHandler {

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
