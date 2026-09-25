package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.time.Period;

import com.clinic.schedule.clinicalrecord.domain.exception.CheckNotPendingException;
import com.clinic.schedule.clinicalrecord.domain.exception.ReasonRequiredException;

/**
 * How a practitioner is granted access to a patient's record to report a check (D-54).
 *
 * <ul>
 *   <li>Only for a check that is still pending: that is what the grant is for, and with an
 *       automatic approval it keeps the grant from opening any record at will.</li>
 *   <li>Approved automatically, for now.</li>
 *   <li>It lasts the configured period from the day it is granted.</li>
 *   <li>A reason is required or optional as configured — whatever the law asks for — and never
 *       judged: nobody checks what it says.</li>
 * </ul>
 */
public class AccessGrantPolicy {

    private final Period validity;
    private final boolean reasonRequired;

    public AccessGrantPolicy(Period validity, boolean reasonRequired) {
        if (validity == null || validity.isNegative() || validity.isZero()) {
            throw new IllegalArgumentException("An access grant must last longer than nothing");
        }
        this.validity = validity;
        this.reasonRequired = reasonRequired;
    }

    /**
     * @throws CheckNotPendingException when the check already has its result
     * @throws ReasonRequiredException when a reason is required and none was given
     */
    public AccessGrant grant(AccessGrantId id, PractitionerId practitionerId, Check check, LocalDateTime now,
            String reason) {
        if (check == null) {
            throw new IllegalArgumentException("An access grant is asked for a check");
        }
        if (!check.isPending()) {
            throw new CheckNotPendingException(check.id());
        }
        if (reasonRequired && (reason == null || reason.isBlank())) {
            throw new ReasonRequiredException();
        }
        return AccessGrant.restore(id, check.patientId(), practitionerId, check.id(), now,
                now.toLocalDate().plus(validity), reason, Approval.AUTOMATIC);
    }

    public boolean isReasonRequired() {
        return reasonRequired;
    }
}
