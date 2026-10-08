package com.clinic.schedule.cashdesk.api;

import java.math.BigDecimal;

/**
 * A visit could not start with that charge, or that coverage (D-87, D-90). The reason is part of the contract, so a caller can
 * tell reception what to fix without knowing this module's own exceptions (D-24). Every reason is a 400.
 */
public class ChargeRefusedException extends RuntimeException {

    public enum Reason {
        /** No amount, for a visit that is not an emergency. */
        CHARGE_REQUIRED,
        /** Not greater than zero, or with more than two decimals. */
        INVALID_AMOUNT,
        /** Above the ceiling, which {@link #ceiling()} says. */
        AMOUNT_TOO_HIGH,
        /** Covered, for a patient with no health insurance recorded. */
        NOT_INSURED,
        /** Covered, for a patient whose identity was not verified. */
        IDENTITY_NOT_VERIFIED,
        /** A token above 50 characters. */
        TOKEN_TOO_LONG
    }

    private final Reason reason;
    private final BigDecimal ceiling;

    /** @param ceiling the most a charge may be, when that is the reason; null otherwise */
    public ChargeRefusedException(Reason reason, String message, BigDecimal ceiling) {
        super(message);
        this.reason = reason;
        this.ceiling = ceiling;
    }

    public Reason reason() {
        return reason;
    }

    /** The most a charge may be; null unless the amount went above it. */
    public BigDecimal ceiling() {
        return ceiling;
    }
}
