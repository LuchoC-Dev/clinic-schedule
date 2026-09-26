package com.clinic.schedule.arrival.eventstore;

/**
 * An event about to be kept: its type — a fixed name, never a class name, so renaming a class
 * does not orphan what was already stored — and its payload.
 */
public record NewEvent(String type, String payload) {

    public NewEvent {
        if (type == null || type.isBlank() || payload == null) {
            throw new IllegalArgumentException("An event needs a type and a payload");
        }
    }
}
