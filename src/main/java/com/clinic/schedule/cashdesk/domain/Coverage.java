package com.clinic.schedule.cashdesk.domain;

import java.time.LocalDateTime;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * A visit covered by the patient's health insurance (D-90): the insurer was asked outside the application and
 * said yes, and this keeps the outcome — the insurance as it was that day, the token the insurer asked for that
 * visit, if any, who recorded it and when. Never changed nor deleted: a coverage marked by mistake is withdrawn,
 * as a fact of its own.
 *
 * @param token what the insurer's check gave for that visit, up to {@value #TOKEN_MAX} characters; null when it
 *        asked for none
 * @param recordedBy the username of whoever recorded it
 * @param withdrawal null unless it was withdrawn
 */
public record Coverage(Insurance insurance, String token, String recordedBy, LocalDateTime recordedAt,
        CoverageWithdrawal withdrawal) {

    static final int TOKEN_MAX = 50;

    public Coverage {
        if (insurance == null || recordedBy == null || recordedBy.isBlank() || recordedAt == null) {
            throw new IllegalArgumentException("A coverage needs its insurance, who recorded it and when");
        }
        token = token == null || token.isBlank() ? null : token.strip();
        if (token != null && token.length() > TOKEN_MAX) {
            throw new ChargeRuleViolationException(Reason.TOKEN_TOO_LONG,
                    "A token is up to " + TOKEN_MAX + " characters");
        }
    }

    /**
     * The insurer said yes: only a patient with an insurance recorded and a verified identity goes in covered,
     * since the insurer validates who the patient is. Anyone else pays privately.
     *
     * @param token what reception typed; blank or null when the insurer asked for none
     */
    public static Coverage claim(Insured patient, String token, String by, LocalDateTime at) {
        if (patient.insurance() == null) {
            throw new ChargeRuleViolationException(Reason.NOT_INSURED, "The patient has no health insurance recorded");
        }
        if (!patient.identityVerified()) {
            throw new ChargeRuleViolationException(Reason.IDENTITY_NOT_VERIFIED,
                    "Only a patient whose identity was verified goes in covered");
        }
        return new Coverage(patient.insurance(), token, by, at, null);
    }

    /** Still standing: not withdrawn. */
    public boolean standing() {
        return withdrawal == null;
    }

    Coverage withdraw(String reason, String by, LocalDateTime at) {
        return new Coverage(insurance, token, recordedBy, recordedAt, new CoverageWithdrawal(reason, by, at));
    }
}
