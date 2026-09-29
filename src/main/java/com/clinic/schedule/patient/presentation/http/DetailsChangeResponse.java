package com.clinic.schedule.patient.presentation.http;

import java.time.LocalDateTime;

import com.clinic.schedule.patient.entity.PatientDetailsChange;

/**
 * A change to one of a patient's details as the API returns it: when, by whom, which detail, and
 * what it was before and after (D-73). A side is null when there was none; its {@code owner} is null
 * when it was the patient's own.
 */
record DetailsChangeResponse(LocalDateTime changedAt, String changedBy, PatientDetailsChange.Field field,
        Value before, Value after) {

    record Value(String value, PatientResponse.Owner owner) {
    }

    static DetailsChangeResponse of(PatientDetailsChange change) {
        return new DetailsChangeResponse(change.getChangedAt(), change.getChangedBy(), change.getField(),
                change.getValueBefore() == null ? null
                        : new Value(change.getValueBefore(), PatientResponse.Owner.of(change.getOwnerBefore())),
                change.getValueAfter() == null ? null
                        : new Value(change.getValueAfter(), PatientResponse.Owner.of(change.getOwnerAfter())));
    }
}
