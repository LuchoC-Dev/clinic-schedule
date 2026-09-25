package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link AttachmentContentRow}; the application talks to {@code AttachmentContentStore}. */
interface AttachmentContentRowRepository extends JpaRepository<AttachmentContentRow, UUID> {
}
