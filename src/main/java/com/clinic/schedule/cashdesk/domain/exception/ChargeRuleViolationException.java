package com.clinic.schedule.cashdesk.domain.exception;

import java.math.BigDecimal;

/**
 * A charge, a refund or a coverage the cash desk's rules refuse, and why (D-87, D-88, D-90). What is missing or
 * malformed in the request, or a patient who cannot go in covered, is a 400; what the visit's charges and coverage
 * as they stand refuse, a 409.
 */
public class ChargeRuleViolationException extends RuntimeException {

    public enum Reason {
        /** A visit that is not an emergency is charged when the patient comes in. */
        CHARGE_REQUIRED,
        /** Not greater than zero, or with more than two decimals. */
        INVALID_AMOUNT,
        /** Above the ceiling, a setting. */
        AMOUNT_TOO_HIGH,
        /** The visit has a charge with something left of it. */
        ALREADY_CHARGED,
        /** The visit has no charge with something left of it to give back. */
        NOTHING_TO_REFUND,
        /** More than what is left of the charge. */
        REFUND_TOO_HIGH,
        /** A refund says why. */
        REASON_REQUIRED,
        /** A refund's reason is up to 200 characters. */
        REASON_TOO_LONG,
        /** Someone else changed the visit's charges meanwhile. */
        CHANGED_MEANWHILE,
        /** A visit goes in covered only for a patient with a health insurance recorded (D-90). */
        NOT_INSURED,
        /** A visit goes in covered only for a patient whose identity was verified (D-90). */
        IDENTITY_NOT_VERIFIED,
        /** A token is up to 50 characters. */
        TOKEN_TOO_LONG,
        /** The visit is covered already, or was charged privately. */
        ALREADY_COVERED,
        /** The visit has no coverage standing to withdraw. */
        NOT_COVERED,
        /** The coverage is withdrawn only once nothing is left of its copay. */
        COPAY_LEFT
    }

    private final Reason reason;

    public ChargeRuleViolationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /** An amount above the ceiling, which the refusal says. */
    public static ChargeRuleViolationException tooHigh(BigDecimal ceiling) {
        return new ChargeRuleViolationException(Reason.AMOUNT_TOO_HIGH,
                "The amount may not be above " + ceiling.toPlainString());
    }

    /** A refund above what is left of the charge, which the refusal says. */
    public static ChargeRuleViolationException refundTooHigh(BigDecimal left) {
        return new ChargeRuleViolationException(Reason.REFUND_TOO_HIGH,
                "A refund may not be above what is left of the charge, " + left.toPlainString());
    }

    public Reason reason() {
        return reason;
    }
}
