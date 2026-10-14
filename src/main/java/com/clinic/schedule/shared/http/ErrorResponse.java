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
 * interface's job (D-13), which it does by {@code code}: the refusal's stable name, left out when there is none
 * (D-100).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String code,
        Map<String, String> fields) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, null, null);
    }

    /** A refusal, named by {@link #codeOf} so a screen can say it in its own words. */
    public static ErrorResponse of(int status, String error, Exception refusal) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, refusal.getMessage(), codeOf(refusal), null);
    }

    public static ErrorResponse ofValidation(String message, Map<String, String> fields) {
        return new ErrorResponse(OffsetDateTime.now(), 400, "Bad request", message, null, fields);
    }

    /**
     * A refusal's stable name: its reason, for an exception that says one — {@code ALREADY_CHARGED} —, and
     * otherwise its class, without "Exception", as {@code PRACTITIONER_ALREADY_BOOKED}. Read here, by its name,
     * so that no module's exception has to know of the API (D-05).
     */
    static String codeOf(Exception refusal) {
        try {
            Object reason = refusal.getClass().getMethod("reason").invoke(refusal);
            if (reason instanceof Enum<?> named) {
                return named.name();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // No reason of its own: its class names it.
        }
        String name = refusal.getClass().getSimpleName().replaceFirst("Exception$", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
    }
}
