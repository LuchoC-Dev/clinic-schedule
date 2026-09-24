package com.clinic.schedule.shared.http;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error body of the whole API. It lives outside the modules because it is a property of
 * the API as a whole: every module answers errors the same shape, so a client has one thing
 * to parse.
 *
 * <p>{@code fields} is only filled in for validation errors and is left out of the JSON
 * otherwise.
 *
 * <p>Messages are in English. Turning them into something a receptionist reads is the user
 * interface's job (D-13).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fields) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, null);
    }

    public static ErrorResponse ofValidation(String message, Map<String, String> fields) {
        return new ErrorResponse(OffsetDateTime.now(), 400, "Bad request", message, fields);
    }
}
