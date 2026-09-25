package com.clinic.schedule.clinicalrecord.domain;

import java.util.UUID;

/** Identity of an addendum, assigned when it is written, so a retried request adds it once (D-19). */
public record AddendumId(UUID value) {

    public AddendumId {
        if (value == null) {
            throw new IllegalArgumentException("An addendum needs an id");
        }
    }

    public static AddendumId newId() {
        return new AddendumId(UUID.randomUUID());
    }
}
