package com.clinic.schedule.consultation.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.consultation.domain.Consultation;
import com.clinic.schedule.consultation.domain.ConsultationId;
import com.clinic.schedule.consultation.domain.ConsultationItem;
import com.clinic.schedule.consultation.domain.PatientId;
import com.clinic.schedule.consultation.domain.PractitionerId;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * The consultation as rows: this one, and one per item in {@code consultation_item}, kept in the
 * order the practitioner wrote them. Separate from the aggregate (D-16), which stays immutable.
 */
@Entity
@Table(name = "consultation")
class ConsultationRow {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false)
    private String notes;

    // Eager: every read turns the row into a consultation at once, and a record holds a handful.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "consultation_item", joinColumns = @JoinColumn(name = "consultation_id"))
    @OrderColumn(name = "position")
    private List<ConsultationItemRow> items = new ArrayList<>();

    protected ConsultationRow() {
        // required by JPA
    }

    static ConsultationRow from(Consultation consultation) {
        ConsultationRow row = new ConsultationRow();
        row.id = consultation.id().value();
        row.patientId = consultation.patientId().value();
        row.practitionerId = consultation.practitionerId().value();
        row.recordedAt = consultation.recordedAt();
        row.notes = consultation.notes();
        row.items = new ArrayList<>(consultation.items().stream().map(ConsultationItemRow::from).toList());
        return row;
    }

    Consultation toConsultation() {
        List<ConsultationItem> read = items.stream().map(ConsultationItemRow::toItem).toList();
        return Consultation.record(new ConsultationId(id), new PatientId(patientId),
                new PractitionerId(practitionerId), recordedAt, notes, read);
    }
}
