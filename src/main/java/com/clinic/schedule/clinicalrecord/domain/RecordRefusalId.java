package com.clinic.schedule.clinicalrecord.domain;

import java.util.UUID;

/** Identity of a refused attempt to reach clinical content, assigned when it is refused (D-19). */
public record RecordRefusalId(UUID value) {

    public RecordRefusalId {
        if (value == null) {
            throw new IllegalArgumentException("A refusal needs an id");
        }
    }

    public static RecordRefusalId newId() {
        return new RecordRefusalId(UUID.randomUUID());
    }
}
