package com.clinic.schedule.patient.presentation.http;

import java.time.LocalDateTime;

import com.clinic.schedule.patient.entity.HealthInsurance;
import com.clinic.schedule.patient.entity.PatientDetailsChange;

/**
 * A change to one of a patient's details as the API returns it: when, by whom, which detail, and
 * what it was before and after (D-73). A side is null when there was none. {@code value} is the date,
 * the phone's digits, the email or the insurer; a phone's or an email's {@code owner} is null when it
 * was the patient's own; {@code memberNumber} and {@code plan} are an insurance's, null for anything
 * else or when not given (D-74).
 */
record DetailsChangeResponse(LocalDateTime changedAt, String changedBy, PatientDetailsChange.Field field,
        Value before, Value after) {

    record Value(String value, PatientResponse.Owner owner, String memberNumber, String plan) {
    }

    static DetailsChangeResponse of(PatientDetailsChange change) {
        if (change.getField() == PatientDetailsChange.Field.INSURANCE) {
            return new DetailsChangeResponse(change.getChangedAt(), change.getChangedBy(), change.getField(),
                    insurance(change.getInsuranceBefore()), insurance(change.getInsuranceAfter()));
        }
        return new DetailsChangeResponse(change.getChangedAt(), change.getChangedBy(), change.getField(),
                change.getValueBefore() == null ? null
                        : new Value(change.getValueBefore(), PatientResponse.Owner.of(change.getOwnerBefore()), null, null),
                change.getValueAfter() == null ? null
                        : new Value(change.getValueAfter(), PatientResponse.Owner.of(change.getOwnerAfter()), null, null));
    }

    private static Value insurance(HealthInsurance insurance) {
        return insurance == null ? null
                : new Value(insurance.insurer(), null, insurance.memberNumber(), insurance.plan());
    }
}
