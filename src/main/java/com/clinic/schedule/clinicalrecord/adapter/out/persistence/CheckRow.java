package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Check;
import com.clinic.schedule.clinicalrecord.domain.CheckId;
import com.clinic.schedule.clinicalrecord.domain.CheckResult;
import com.clinic.schedule.clinicalrecord.domain.ConsultationId;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A check as a row of {@code clinical_check} — {@code check} is a reserved word in SQL. The result
 * lives in the same row, its three columns empty while it is pending. Separate from the aggregate
 * (D-16), which stays immutable.
 *
 * <p>The version guards the result: two practitioners recording one at the same time both read
 * the same version, and the second write is refused instead of replacing the first (D-53).
 */
@Entity
@Table(name = "clinical_check")
class CheckRow {

    @Id
    private UUID id;

    @Version
    private long version;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "performed_by", nullable = false)
    private UUID performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(nullable = false)
    private String description;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "result_author")
    private UUID resultAuthor;

    @Column(name = "result_written_at")
    private LocalDateTime resultWrittenAt;

    @Column(name = "result_text")
    private String resultText;

    protected CheckRow() {
        // required by JPA
    }

    static CheckRow from(Check check) {
        CheckRow row = new CheckRow();
        row.id = check.id().value();
        row.patientId = check.patientId().value();
        row.performedBy = check.performedBy().value();
        row.performedAt = check.performedAt();
        row.description = check.description();
        row.consultationId = check.consultationId().map(ConsultationId::value).orElse(null);
        row.resultFrom(check);
        return row;
    }

    /** Writes the result the check gained onto this row, as read in this transaction: nothing else changes. */
    void resultFrom(Check check) {
        check.result().ifPresent(result -> {
            resultAuthor = result.author().value();
            resultWrittenAt = result.writtenAt();
            resultText = result.text();
        });
    }

    Check toCheck() {
        CheckResult result = resultText == null ? null
                : new CheckResult(new PractitionerId(resultAuthor), resultWrittenAt, resultText);
        return Check.restore(new CheckId(id), new PatientId(patientId), new PractitionerId(performedBy), performedAt,
                description, consultationId == null ? null : new ConsultationId(consultationId), result);
    }
}
