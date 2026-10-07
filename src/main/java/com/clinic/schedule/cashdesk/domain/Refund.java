package com.clinic.schedule.cashdesk.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * Money given back from a charge (D-88): a movement of its own, apart from the charge, with its amount, why,
 * who recorded it and when. Never changed nor deleted, as a charge.
 *
 * @param reason why it was given back, up to {@value #REASON_MAX} characters, trimmed
 * @param recordedBy the username of whoever recorded it
 */
public record Refund(UUID id, Amount amount, String reason, String recordedBy, LocalDateTime recordedAt) {

    static final int REASON_MAX = 200;

    public Refund {
        if (id == null || amount == null || recordedBy == null || recordedBy.isBlank() || recordedAt == null) {
            throw new IllegalArgumentException("A refund needs its amount, who recorded it and when");
        }
        reason = reason == null ? "" : reason.strip();
        if (reason.isEmpty()) {
            throw new ChargeRuleViolationException(Reason.REASON_REQUIRED, "A refund says why");
        }
        if (reason.length() > REASON_MAX) {
            throw new ChargeRuleViolationException(Reason.REASON_TOO_LONG,
                    "A refund's reason is up to " + REASON_MAX + " characters");
        }
    }
}
