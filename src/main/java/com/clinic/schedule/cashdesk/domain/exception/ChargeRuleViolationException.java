package com.clinic.schedule.cashdesk.domain.exception;

import java.math.BigDecimal;

/**
 * A charge the cash desk's rules refuse, and why (D-87). An amount missing or out of bounds is a 400; a
 * visit charged already, a 409.
 */
public class ChargeRuleViolationException extends RuntimeException {

    public enum Reason {
        /** A visit that is not an emergency is charged when the patient comes in. */
        CHARGE_REQUIRED,
        /** Not greater than zero, or with more than two decimals. */
        INVALID_AMOUNT,
        /** Above the ceiling, a setting. */
        AMOUNT_TOO_HIGH,
        /** The visit was charged already. */
        ALREADY_CHARGED
    }

    private final Reason reason;
    private final BigDecimal ceiling;

    public ChargeRuleViolationException(Reason reason, String message) {
        this(reason, message, null);
    }

    private ChargeRuleViolationException(Reason reason, String message, BigDecimal ceiling) {
        super(message);
        this.reason = reason;
        this.ceiling = ceiling;
    }

    /** An amount above the ceiling, which the refusal carries so the screens can say it. */
    public static ChargeRuleViolationException tooHigh(BigDecimal ceiling) {
        return new ChargeRuleViolationException(Reason.AMOUNT_TOO_HIGH,
                "The amount may not be above " + ceiling.toPlainString(), ceiling);
    }

    public Reason reason() {
        return reason;
    }

    /** The ceiling the amount went above; null for any other reason. */
    public BigDecimal ceiling() {
        return ceiling;
    }
}
