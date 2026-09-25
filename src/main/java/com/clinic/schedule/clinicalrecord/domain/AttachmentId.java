package com.clinic.schedule.clinicalrecord.domain;

import java.util.UUID;

/** Identity of an attachment, assigned when it is uploaded; also the key its content is stored under (D-57). */
public record AttachmentId(UUID value) {

    public AttachmentId {
        if (value == null) {
            throw new IllegalArgumentException("An attachment needs an id");
        }
    }

    public static AttachmentId newId() {
        return new AttachmentId(UUID.randomUUID());
    }
}
