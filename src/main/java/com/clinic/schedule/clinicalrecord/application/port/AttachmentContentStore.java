package com.clinic.schedule.clinicalrecord.application.port;

import java.util.Optional;

import com.clinic.schedule.clinicalrecord.domain.AttachmentId;

/**
 * Where the files attached to a clinical record live, under each attachment's id (D-57). Storage,
 * but of bytes, not of records, and meant to move: in the database today, in an object store
 * (S3, MinIO) when the files outgrow it, by changing the adapter alone. Files are only added.
 */
public interface AttachmentContentStore {

    void put(AttachmentId id, byte[] content);

    Optional<byte[]> get(AttachmentId id);
}
