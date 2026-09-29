package com.clinic.schedule.patient.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A change to one of a patient's details (D-73): which one, what it was and what it became, with who
 * changed it and when. One entry per detail, so a save that changes the phone and the email leaves
 * two.
 *
 * <p>The value is the detail as it is kept — a date as {@code 1981-03-12}, a phone's digits, an email,
 * an insurer's name — and null when there was none. The person a phone or an email belonged to is kept
 * apart, so a screen can say it in its own language, and so are an insurance's member number and plan
 * (D-74), each part in a column of its own.
 *
 * <p>Only {@link Patient#changeDetails} makes one, and nothing changes one once made.
 */
@Entity
@Table(name = "patient_details_change")
public class PatientDetailsChange {

    /** Which detail changed. */
    public enum Field {
        BIRTH_DATE, PHONE, EMAIL, INSURANCE
    }

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Field field;

    @Column(name = "value_before", length = 254)
    private String valueBefore;

    @Column(name = "owner_name_before", length = 150)
    private String ownerNameBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_relation_before", length = 20)
    private Relation ownerRelationBefore;

    @Column(name = "member_number_before", length = 30)
    private String memberNumberBefore;

    @Column(name = "plan_before", length = 50)
    private String planBefore;

    @Column(name = "value_after", length = 254)
    private String valueAfter;

    @Column(name = "owner_name_after", length = 150)
    private String ownerNameAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_relation_after", length = 20)
    private Relation ownerRelationAfter;

    @Column(name = "member_number_after", length = 30)
    private String memberNumberAfter;

    @Column(name = "plan_after", length = 50)
    private String planAfter;

    protected PatientDetailsChange() {
        // required by JPA
    }

    private PatientDetailsChange(UUID patientId, LocalDateTime changedAt, String changedBy, Field field) {
        if (changedAt == null || changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException("A change of a patient's details needs when and by whom");
        }
        this.id = UUID.randomUUID();
        this.patientId = patientId;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
        this.field = field;
    }

    /** A change to a detail that is a value, maybe with the person it belongs to. */
    static PatientDetailsChange of(UUID patientId, LocalDateTime changedAt, String changedBy, Field field,
            String valueBefore, Responsible ownerBefore, String valueAfter, Responsible ownerAfter) {
        PatientDetailsChange change = new PatientDetailsChange(patientId, changedAt, changedBy, field);
        change.valueBefore = valueBefore;
        change.ownerNameBefore = ownerBefore == null ? null : ownerBefore.name();
        change.ownerRelationBefore = ownerBefore == null ? null : ownerBefore.relation();
        change.valueAfter = valueAfter;
        change.ownerNameAfter = ownerAfter == null ? null : ownerAfter.name();
        change.ownerRelationAfter = ownerAfter == null ? null : ownerAfter.relation();
        return change;
    }

    /** A change to the health insurance: the insurer as the value, the member number and plan apart. */
    static PatientDetailsChange ofInsurance(UUID patientId, LocalDateTime changedAt, String changedBy,
            HealthInsurance before, HealthInsurance after) {
        PatientDetailsChange change = new PatientDetailsChange(patientId, changedAt, changedBy, Field.INSURANCE);
        change.valueBefore = before == null ? null : before.insurer();
        change.memberNumberBefore = before == null ? null : before.memberNumber();
        change.planBefore = before == null ? null : before.plan();
        change.valueAfter = after == null ? null : after.insurer();
        change.memberNumberAfter = after == null ? null : after.memberNumber();
        change.planAfter = after == null ? null : after.plan();
        return change;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    /** The username of whoever changed it. */
    public String getChangedBy() {
        return changedBy;
    }

    public Field getField() {
        return field;
    }

    /** Null when there was none. */
    public String getValueBefore() {
        return valueBefore;
    }

    /** Null when it was the patient's own, or there was none. */
    public Responsible getOwnerBefore() {
        return ownerRelationBefore == null ? null : new Responsible(ownerNameBefore, ownerRelationBefore);
    }

    /** The insurance it was, for a change to the insurance; null when there was none. */
    public HealthInsurance getInsuranceBefore() {
        return field != Field.INSURANCE || valueBefore == null ? null
                : new HealthInsurance(valueBefore, memberNumberBefore, planBefore);
    }

    /** Null when there is none. */
    public String getValueAfter() {
        return valueAfter;
    }

    /** Null when it is the patient's own, or there is none. */
    public Responsible getOwnerAfter() {
        return ownerRelationAfter == null ? null : new Responsible(ownerNameAfter, ownerRelationAfter);
    }

    /** The insurance it became, for a change to the insurance; null when there is none. */
    public HealthInsurance getInsuranceAfter() {
        return field != Field.INSURANCE || valueAfter == null ? null
                : new HealthInsurance(valueAfter, memberNumberAfter, planAfter);
    }
}
