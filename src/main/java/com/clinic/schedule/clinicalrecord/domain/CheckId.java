package com.clinic.schedule.clinicalrecord.domain;

import java.util.UUID;

/** Identity of a check, assigned when it is recorded, so a retried request records it once (D-19). */
public record CheckId(UUID value) {

    public CheckId {
        if (value == null) {
            throw new IllegalArgumentException("A check needs an id");
        }
    }

    public static CheckId newId() {
        return new CheckId(UUID.randomUUID());
    }
}
