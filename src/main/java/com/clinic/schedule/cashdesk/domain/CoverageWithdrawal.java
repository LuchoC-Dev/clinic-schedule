package com.clinic.schedule.cashdesk.domain;

import java.time.LocalDateTime;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * A visit marked covered by mistake, set right (D-90): a fact of its own beside the coverage, which is never
 * changed nor deleted, with why, who recorded it and when.
 *
 * @param reason why, up to {@value Refund#REASON_MAX} characters, trimmed, as a refund's
 * @param recordedBy the username of whoever recorded it
 */
public record CoverageWithdrawal(String reason, String recordedBy, LocalDateTime recordedAt) {

    public CoverageWithdrawal {
        if (recordedBy == null || recordedBy.isBlank() || recordedAt == null) {
            throw new IllegalArgumentException("A coverage withdrawn says who withdrew it and when");
        }
        reason = reason == null ? "" : reason.strip();
        if (reason.isEmpty()) {
            throw new ChargeRuleViolationException(Reason.REASON_REQUIRED, "A coverage withdrawn says why");
        }
        if (reason.length() > Refund.REASON_MAX) {
            throw new ChargeRuleViolationException(Reason.REASON_TOO_LONG,
                    "The reason is up to " + Refund.REASON_MAX + " characters");
        }
    }
}
