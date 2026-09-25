package com.clinic.schedule.consultation.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.consultation.domain.Addendum;
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
import jakarta.persistence.Version;

/**
 * The consultation as rows: this one, one per item in {@code consultation_item} and one per
 * addendum in {@code consultation_addendum}, each list kept in the order it was written.
 * Separate from the aggregate (D-16), which stays immutable.
 *
 * <p>The version guards the addenda: two practitioners adding one at the same time both read
 * the same version, and the second write is refused instead of overwriting the first (D-52).
 */
@Entity
@Table(name = "consultation")
class ConsultationRow {

    @Id
    private UUID id;

    @Version
    private long version;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false)
    private String notes;

    // Eager: every read turns the row into a consultation at once, and a record holds a handful.
    // Both lists are indexed by their order column, so Hibernate can fetch them together.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "consultation_item", joinColumns = @JoinColumn(name = "consultation_id"))
    @OrderColumn(name = "position")
    private List<ConsultationItemRow> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "consultation_addendum", joinColumns = @JoinColumn(name = "consultation_id"))
    @OrderColumn(name = "position")
    private List<AddendumRow> addenda = new ArrayList<>();

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
        row.addenda = new ArrayList<>(consultation.addenda().stream().map(AddendumRow::from).toList());
        return row;
    }

    /**
     * Writes the addenda the consultation gained onto this row, as read in this transaction. The
     * rest never changes (D-47), so there is nothing else to copy.
     */
    void addAddendaFrom(Consultation consultation) {
        List<Addendum> all = consultation.addenda();
        for (int i = addenda.size(); i < all.size(); i++) {
            addenda.add(AddendumRow.from(all.get(i)));
        }
    }

    Consultation toConsultation() {
        return Consultation.restore(new ConsultationId(id), new PatientId(patientId),
                new PractitionerId(practitionerId), recordedAt, notes,
                items.stream().map(ConsultationItemRow::toItem).toList(),
                addenda.stream().map(AddendumRow::toAddendum).toList());
    }
}
