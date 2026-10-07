package com.clinic.schedule.cashdesk.domain;

import java.math.BigDecimal;

/**
 * The most a single charge may be (D-87), a setting. A rule of the business, not of the column: it stops an
 * amount typed with too many digits with a refusal reception reads, never with the database cutting it, so
 * it must itself fit the column, NUMERIC(12,2).
 */
public record AmountCeiling(BigDecimal value) {

    /** The largest amount the column keeps. */
    static final BigDecimal COLUMN_MAX = new BigDecimal("9999999999.99");

    public AmountCeiling {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 2
                || value.compareTo(COLUMN_MAX) > 0) {
            throw new IllegalArgumentException("The ceiling of an amount is greater than zero, with two decimals at "
                    + "most, and no more than " + COLUMN_MAX.toPlainString());
        }
    }
}
