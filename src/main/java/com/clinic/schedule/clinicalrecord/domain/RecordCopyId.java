package com.clinic.schedule.clinicalrecord.domain;

import java.util.UUID;

/** Identity of a request for a copy of a clinical record, assigned when the patient asks for it (D-95). */
public record RecordCopyId(UUID value) {

    public RecordCopyId {
        if (value == null) {
            throw new IllegalArgumentException("A copy of a record needs an id");
        }
    }

    public static RecordCopyId newId() {
        return new RecordCopyId(UUID.randomUUID());
    }
}
