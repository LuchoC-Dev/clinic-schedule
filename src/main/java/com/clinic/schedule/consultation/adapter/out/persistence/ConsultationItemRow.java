package com.clinic.schedule.consultation.adapter.out.persistence;

import com.clinic.schedule.consultation.domain.ConsultationItem;
import com.clinic.schedule.consultation.domain.ItemKind;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** One item of a consultation as a row of {@code consultation_item}. */
@Embeddable
class ConsultationItemRow {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemKind kind;

    @Column(nullable = false)
    private String description;

    protected ConsultationItemRow() {
        // required by JPA
    }

    static ConsultationItemRow from(ConsultationItem item) {
        ConsultationItemRow row = new ConsultationItemRow();
        row.kind = item.kind();
        row.description = item.description();
        return row;
    }

    ConsultationItem toItem() {
        return new ConsultationItem(kind, description);
    }
}
