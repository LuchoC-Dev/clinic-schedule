package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.clinic.schedule.cashdesk.domain.Amount;
import com.clinic.schedule.cashdesk.domain.Charge;
import com.clinic.schedule.cashdesk.domain.Means;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A charge as a row (D-87). Only ever inserted, never changed nor deleted. A visit has one at most: the
 * unique key on the visit refuses a second that two requests at once could let through.
 */
@Entity
@Immutable
@Table(name = "cash_charge")
class ChargeRow {

    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Means means;

    @Column(name = "recorded_by", nullable = false, length = 50)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected ChargeRow() {
        // required by JPA
    }

    static ChargeRow from(UUID visitId, Charge charge) {
        ChargeRow row = new ChargeRow();
        row.id = charge.id();
        row.visitId = visitId;
        row.amount = charge.amount().value();
        row.means = charge.means();
        row.recordedBy = charge.recordedBy();
        row.recordedAt = charge.recordedAt();
        return row;
    }

    UUID visitId() {
        return visitId;
    }

    Charge toCharge() {
        return new Charge(id, new Amount(amount), means, recordedBy, recordedAt);
    }
}
