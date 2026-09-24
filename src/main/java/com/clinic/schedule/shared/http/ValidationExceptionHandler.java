package com.clinic.schedule.shared.http;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;

/**
 * A request the API cannot even read — invalid fields, a body that is not JSON, a value of
 * the wrong type, a missing parameter — answered the same way for every module: a 400 with
 * the uniform error body, naming what is wrong and nothing of the code behind it (D-38).
 *
 * <p>Global, unlike the modules' own advices. It imports nothing from any module — only
 * Spring's and Jackson's exceptions — so sharing it does not couple them (D-23). It was
 * duplicated in the first two modules and moved here with the third, once it was clear every
 * module wanted the same shape.
 *
 * <p>It answers the REST controllers only; the screens have their own error page. It goes
 * first, because the modules' advices would otherwise take a malformed id for one of their
 * own refusals through its cause.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
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

    /** Not JSON at all, or a field whose value cannot be read as its type — an unknown document type, say. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(reference -> reference.getFieldName() != null
                            ? reference.getFieldName() : "[" + reference.getIndex() + "]")
                    .collect(Collectors.joining("."));
            return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(
                    "The request contains invalid fields", Map.of(field, "has a value of the wrong type")));
        }
        return ResponseEntity.badRequest().body(badRequest("The request body is not valid JSON"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> wrongType(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(badRequest("Parameter '" + e.getName() + "' has an invalid value"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(badRequest("Parameter '" + e.getParameterName() + "' is required"));
    }

    private static ErrorResponse badRequest(String message) {
        return ErrorResponse.of(400, "Bad request", message);
    }
}
