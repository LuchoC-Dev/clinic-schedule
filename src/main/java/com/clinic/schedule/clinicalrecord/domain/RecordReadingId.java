package com.clinic.schedule.clinicalrecord.domain;

import java.util.UUID;

/** Identity of a reading of a clinical record, assigned when it is read (D-19). */
public record RecordReadingId(UUID value) {

    public RecordReadingId {
        if (value == null) {
            throw new IllegalArgumentException("A reading of a record needs an id");
        }
    }

    public static RecordReadingId newId() {
        return new RecordReadingId(UUID.randomUUID());
    }
}
