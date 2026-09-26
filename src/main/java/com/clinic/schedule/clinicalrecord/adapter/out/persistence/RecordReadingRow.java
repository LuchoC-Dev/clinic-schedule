package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.AttachmentId;
import com.clinic.schedule.clinicalrecord.domain.ReadAccess;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A reading as a row of {@code record_reading}. Separate from the domain object (D-16). Written
 * only, so there is no way back to a {@link RecordReading} until something reads them.
 */
@Entity
@Table(name = "record_reading")
class RecordReadingRow {

    @Id
    private UUID id;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReadAccess access;

    @Column(name = "attachment_id")
    private UUID attachmentId;

    protected RecordReadingRow() {
        // required by JPA
    }

    static RecordReadingRow from(RecordReading reading) {
        RecordReadingRow row = new RecordReadingRow();
        row.id = reading.id().value();
        row.practitionerId = reading.practitionerId().value();
        row.patientId = reading.patientId().value();
        row.readAt = reading.readAt();
        row.access = reading.access();
        row.attachmentId = reading.attachmentId().map(AttachmentId::value).orElse(null);
        return row;
    }
}
