package com.clinic.schedule.cashdesk.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

/**
 * An amount of pesos charged (D-87): greater than zero, with two decimals at most. There is no price list:
 * reception types it.
 *
 * <p>A typed amount is held to the ceiling as well, through {@link #typed}. One read back is not: the
 * ceiling is a setting, and lowering it does not undo what was charged under the old one.
 */
public record Amount(BigDecimal value) {

    public Amount {
        if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) {
            throw new ChargeRuleViolationException(Reason.INVALID_AMOUNT,
                    "An amount is greater than zero, with two decimals at most");
        }
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** What reception typed, held to the ceiling too. */
    public static Amount typed(BigDecimal value, AmountCeiling ceiling) {
        Amount amount = new Amount(value);
        if (amount.value.compareTo(ceiling.value()) > 0) {
            throw ChargeRuleViolationException.tooHigh(ceiling.value());
        }
        return amount;
    }
}
