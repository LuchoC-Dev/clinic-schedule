package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.clinic.schedule.cashdesk.domain.Amount;
import com.clinic.schedule.cashdesk.domain.Refund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A refund as a row (D-88). Only ever inserted, never changed nor deleted. Its number is its place among the
 * charge's refunds: two requests at once giving back from the charge take the same one, and the unique key
 * on the pair refuses the second, so no more is given back than was charged.
 */
@Entity
@Immutable
@Table(name = "cash_refund")
class RefundRow {

    @Id
    private UUID id;

    @Column(name = "charge_id", nullable = false)
    private UUID chargeId;

    @Column(name = "refund_no", nullable = false)
    private int number;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "recorded_by", nullable = false, length = 50)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected RefundRow() {
        // required by JPA
    }

    /** @param number its place among the charge's refunds, from 1 */
    static RefundRow from(UUID chargeId, int number, Refund refund) {
        RefundRow row = new RefundRow();
        row.id = refund.id();
        row.chargeId = chargeId;
        row.number = number;
        row.amount = refund.amount().value();
        row.reason = refund.reason();
        row.recordedBy = refund.recordedBy();
        row.recordedAt = refund.recordedAt();
        return row;
    }

    UUID chargeId() {
        return chargeId;
    }

    int number() {
        return number;
    }

    Refund toRefund() {
        return new Refund(id, new Amount(amount), reason, recordedBy, recordedAt);
    }
}
