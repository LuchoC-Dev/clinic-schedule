package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.clinic.schedule.cashdesk.domain.CoverageWithdrawal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A coverage withdrawn as a row (D-90): only ever inserted, keyed by its visit, so it is withdrawn once. */
@Entity
@Immutable
@Table(name = "cash_coverage_withdrawal")
class CoverageWithdrawalRow {

    @Id
    @Column(name = "visit_id")
    private UUID visitId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "recorded_by", nullable = false, length = 50)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected CoverageWithdrawalRow() {
        // required by JPA
    }

    static CoverageWithdrawalRow from(UUID visitId, CoverageWithdrawal withdrawal) {
        CoverageWithdrawalRow row = new CoverageWithdrawalRow();
        row.visitId = visitId;
        row.reason = withdrawal.reason();
        row.recordedBy = withdrawal.recordedBy();
        row.recordedAt = withdrawal.recordedAt();
        return row;
    }

    UUID visitId() {
        return visitId;
    }

    /** The same withdrawal as the other row: a coverage is withdrawn once. */
    boolean sameAs(CoverageWithdrawalRow other) {
        return visitId.equals(other.visitId) && reason.equals(other.reason) && recordedBy.equals(other.recordedBy)
                && recordedAt.equals(other.recordedAt);
    }

    CoverageWithdrawal toWithdrawal() {
        return new CoverageWithdrawal(reason, recordedBy, recordedAt);
    }
}
