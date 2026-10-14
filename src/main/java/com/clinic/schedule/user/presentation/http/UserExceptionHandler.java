package com.clinic.schedule.user.presentation.http;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.clinic.schedule.shared.http.ErrorResponse;
import com.clinic.schedule.user.entity.UserStatusException;
import com.clinic.schedule.user.service.PractitionerTakenException;
import com.clinic.schedule.user.service.SelfDeactivationException;
import com.clinic.schedule.user.service.UnknownPractitionerException;
import com.clinic.schedule.user.service.UserNotFoundException;
import com.clinic.schedule.user.service.UsernameTakenException;

/**
 * This module's exceptions as HTTP answers, scoped to its controller (D-23).
 *
 * <p>An unknown user is a 404, not the 403 of the clinical record (D-49): only admins reach these
 * routes, and they may see every user.
 */
@RestControllerAdvice(assignableTypes = UserController.class)
class UserExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not found", e));
    }

    @ExceptionHandler({UsernameTakenException.class, PractitionerTakenException.class,
            SelfDeactivationException.class, UserStatusException.class})
    ResponseEntity<ErrorResponse> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e));
    }

    /** A malformed username or password, a role without its practitioner, or an unknown practitioner. */
    @ExceptionHandler({UnknownPractitionerException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> malformedRequest(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad request", e));
    }
}
