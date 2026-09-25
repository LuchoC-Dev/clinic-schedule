package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.port.AttachmentContentStore;
import com.clinic.schedule.clinicalrecord.domain.AttachmentId;

/**
 * Keeps the attached files in the database, in a table of their own (D-57). The simplest store for
 * a small practice: the file and what describes it are written in the same transaction, and they
 * are backed up together. When the files outgrow the database, an adapter over an object store
 * replaces this one and nothing else changes.
 */
@Repository
class AttachmentContentJpaAdapter implements AttachmentContentStore {

    private final AttachmentContentRowRepository rows;

    AttachmentContentJpaAdapter(AttachmentContentRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void put(AttachmentId id, byte[] content) {
        rows.save(new AttachmentContentRow(id.value(), content));
    }

    @Override
    public Optional<byte[]> get(AttachmentId id) {
        return rows.findById(id.value()).map(AttachmentContentRow::content);
    }
}
