package com.clinic.schedule.patient.presentation.http;

import java.time.LocalDateTime;

import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IdentityStatus;
import com.clinic.schedule.patient.entity.PatientIdentityChange;

/** A change to a patient's identity as the API returns it: when, by whom, and what it was before and after (D-60). */
record IdentityChangeResponse(LocalDateTime changedAt, String changedBy, Identity before, Identity after) {

    record Identity(String fullName, PatientResponse.Document document, IdentityStatus identity) {

        static Identity of(String fullName, IdentityDocument document, IdentityStatus identity) {
            return new Identity(fullName,
                    document == null ? null : new PatientResponse.Document(document.type(), document.number()),
                    identity);
        }
    }

    static IdentityChangeResponse of(PatientIdentityChange change) {
        return new IdentityChangeResponse(change.getChangedAt(), change.getChangedBy(),
                Identity.of(change.getFullNameBefore(), change.getDocumentBefore(), change.getStatusBefore()),
                Identity.of(change.getFullNameAfter(), change.getDocumentAfter(), change.getStatusAfter()));
    }
}
