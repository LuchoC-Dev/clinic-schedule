package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.clinic.schedule.cashdesk.domain.Charge;
import com.clinic.schedule.cashdesk.domain.Visit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A visit as a row (D-87). Only ever inserted: its charge is a row of its own, added once. */
@Entity
@Immutable
@Table(name = "cash_visit")
class VisitRow {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "arrived_at", nullable = false)
    private LocalDateTime arrivedAt;

    @Column(nullable = false)
    private boolean emergency;

    protected VisitRow() {
        // required by JPA
    }

    static VisitRow from(Visit visit) {
        VisitRow row = new VisitRow();
        row.id = visit.id();
        row.patientId = visit.patientId();
        row.arrivedAt = visit.arrivedAt();
        row.emergency = visit.emergency();
        return row;
    }

    UUID id() {
        return id;
    }

    /** @param charge its charge; null when it has none yet */
    Visit toVisit(Charge charge) {
        return Visit.recorded(id, patientId, arrivedAt, emergency, charge);
    }
}
