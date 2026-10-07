package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.clinic.schedule.cashdesk.domain.Amount;
import com.clinic.schedule.cashdesk.domain.Charge;
import com.clinic.schedule.cashdesk.domain.Means;
import com.clinic.schedule.cashdesk.domain.Refund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A charge as a row (D-87). Only ever inserted, never changed nor deleted. Its number is its place among the
 * visit's charges (D-88): two requests at once charging the visit take the same one, and the unique key on
 * the pair refuses the second. Its refunds are rows of their own.
 */
@Entity
@Immutable
@Table(name = "cash_charge")
class ChargeRow {

    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "charge_no", nullable = false)
    private int number;

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

    /** @param number its place among the visit's charges, from 1 */
    static ChargeRow from(UUID visitId, int number, Charge charge) {
        ChargeRow row = new ChargeRow();
        row.id = charge.id();
        row.visitId = visitId;
        row.number = number;
        row.amount = charge.amount().value();
        row.means = charge.means();
        row.recordedBy = charge.recordedBy();
        row.recordedAt = charge.recordedAt();
        return row;
    }

    UUID id() {
        return id;
    }

    UUID visitId() {
        return visitId;
    }

    int number() {
        return number;
    }

    /** @param refunds its refunds, in the order they were recorded */
    Charge toCharge(List<Refund> refunds) {
        return new Charge(id, new Amount(amount), means, recordedBy, recordedAt, refunds);
    }
}
