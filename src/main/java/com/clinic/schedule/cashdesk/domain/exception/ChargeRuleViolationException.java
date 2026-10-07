package com.clinic.schedule.cashdesk.domain.exception;

import java.math.BigDecimal;

/**
 * A charge or a refund the cash desk's rules refuse, and why (D-87, D-88). What is missing or malformed in
 * the request is a 400; what the visit's charges as they stand refuse, a 409.
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
        CHANGED_MEANWHILE
    }

    private final Reason reason;
    private final BigDecimal limit;

    public ChargeRuleViolationException(Reason reason, String message) {
        this(reason, message, null);
    }

    private ChargeRuleViolationException(Reason reason, String message, BigDecimal limit) {
        super(message);
        this.reason = reason;
        this.limit = limit;
    }

    /** An amount above the ceiling, which the refusal carries so the screens can say it. */
    public static ChargeRuleViolationException tooHigh(BigDecimal ceiling) {
        return new ChargeRuleViolationException(Reason.AMOUNT_TOO_HIGH,
                "The amount may not be above " + ceiling.toPlainString(), ceiling);
    }

    /** A refund above what is left of the charge, which the refusal carries so the screens can say it. */
    public static ChargeRuleViolationException refundTooHigh(BigDecimal left) {
        return new ChargeRuleViolationException(Reason.REFUND_TOO_HIGH,
                "A refund may not be above what is left of the charge, " + left.toPlainString(), left);
    }

    public Reason reason() {
        return reason;
    }

    /** The ceiling, or what was left of the charge, that the amount went above; null for any other reason. */
    public BigDecimal limit() {
        return limit;
    }
}
