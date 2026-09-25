package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The bytes of an attached file, as a row of {@code attachment_content}, under the attachment's id (D-57). */
@Entity
@Table(name = "attachment_content")
class AttachmentContentRow {

    @Id
    @Column(name = "attachment_id")
    private UUID attachmentId;

    @Column(nullable = false)
    private byte[] content;

    protected AttachmentContentRow() {
        // required by JPA
    }

    AttachmentContentRow(UUID attachmentId, byte[] content) {
        this.attachmentId = attachmentId;
        this.content = content;
    }

    byte[] content() {
        return content;
    }
}
