package com.clinic.schedule.cashdesk.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.clinic.schedule.cashdesk.domain.Coverage;
import com.clinic.schedule.cashdesk.domain.CoverageWithdrawal;
import com.clinic.schedule.cashdesk.domain.Insurance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A visit's coverage as a row (D-90): the patient's insurance as it was that day, and the token. Only ever
 * inserted, keyed by its visit, so two requests at once cannot both cover it. Its withdrawal is a row of its own.
 */
@Entity
@Immutable
@Table(name = "cash_coverage")
class CoverageRow {

    @Id
    @Column(name = "visit_id")
    private UUID visitId;

    @Column(nullable = false, length = 100)
    private String insurer;

    @Column(name = "member_number", length = 30)
    private String memberNumber;

    @Column(length = 50)
    private String plan;

    @Column(length = 50)
    private String token;

    @Column(name = "recorded_by", nullable = false, length = 50)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected CoverageRow() {
        // required by JPA
    }

    static CoverageRow from(UUID visitId, Coverage coverage) {
        CoverageRow row = new CoverageRow();
        row.visitId = visitId;
        row.insurer = coverage.insurance().insurer();
        row.memberNumber = coverage.insurance().memberNumber();
        row.plan = coverage.insurance().plan();
        row.token = coverage.token();
        row.recordedBy = coverage.recordedBy();
        row.recordedAt = coverage.recordedAt();
        return row;
    }

    UUID visitId() {
        return visitId;
    }

    /** The same coverage as the other row: a visit is covered once. */
    boolean sameAs(CoverageRow other) {
        return visitId.equals(other.visitId) && insurer.equals(other.insurer)
                && Objects.equals(memberNumber, other.memberNumber) && Objects.equals(plan, other.plan)
                && Objects.equals(token, other.token) && recordedBy.equals(other.recordedBy)
                && recordedAt.equals(other.recordedAt);
    }

    /** @param withdrawal null unless it was withdrawn */
    Coverage toCoverage(CoverageWithdrawal withdrawal) {
        return new Coverage(new Insurance(insurer, memberNumber, plan), token, recordedBy, recordedAt, withdrawal);
    }
}
