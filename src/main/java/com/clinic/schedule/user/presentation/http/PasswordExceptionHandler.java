package com.clinic.schedule.user.presentation.http;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.shared.http.ErrorResponse;
import com.clinic.schedule.user.service.UnchangedPasswordException;
import com.clinic.schedule.user.service.WrongPasswordException;

/**
 * The refusals of changing one's password, scoped to its controller (D-23). All three are the
 * request's fault, so a 400: a wrong current password is not a 401, since the user is logged in.
 */
@RestControllerAdvice(assignableTypes = PasswordController.class)
class PasswordExceptionHandler {

    @ExceptionHandler({WrongPasswordException.class, UnchangedPasswordException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> refused(RuntimeException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad request", e.getMessage()));
    }
}
