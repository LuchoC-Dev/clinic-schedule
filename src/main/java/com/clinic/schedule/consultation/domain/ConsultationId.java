package com.clinic.schedule.consultation.domain;

import java.util.UUID;

/**
 * Identity of a consultation, assigned when it is created rather than by the database, so a
 * retried request can reuse it and not record the consultation twice (D-19).
 */
public record ConsultationId(UUID value) {

    public ConsultationId {
        if (value == null) {
            throw new IllegalArgumentException("A consultation needs an id");
        }
    }

    public static ConsultationId newId() {
        return new ConsultationId(UUID.randomUUID());
    }
}
