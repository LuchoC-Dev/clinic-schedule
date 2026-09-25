package com.clinic.schedule.clinicalrecord.domain;

import java.util.UUID;

/** Identity of an access grant, assigned when it is granted (D-19). */
public record AccessGrantId(UUID value) {

    public AccessGrantId {
        if (value == null) {
            throw new IllegalArgumentException("An access grant needs an id");
        }
    }

    public static AccessGrantId newId() {
        return new AccessGrantId(UUID.randomUUID());
    }
}
