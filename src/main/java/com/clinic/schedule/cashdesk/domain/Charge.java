package com.clinic.schedule.cashdesk.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;

/**
 * What was charged for a visit (D-87): the amount, the means, who recorded it and when, and what was given
 * back of it since, in the order it was (D-88). It is never changed nor deleted: a refund is a movement of its
 * own, and giving one back answers a new charge with it added. Never more is given back than was charged, and
 * what is given back goes out by the charge's means (D-89).
 *
 * @param recordedBy the username of whoever recorded it
 */
public record Charge(UUID id, Amount amount, Means means, String recordedBy, LocalDateTime recordedAt,
        List<Refund> refunds) {

    public Charge {
        if (id == null || amount == null || means == null || recordedBy == null || recordedBy.isBlank()
                || recordedAt == null || refunds == null) {
            throw new IllegalArgumentException("A charge needs its amount, its means, who recorded it and when");
        }
        refunds = List.copyOf(refunds);
        if (refunded(refunds).compareTo(amount.value()) > 0) {
            throw new IllegalArgumentException("A charge gives back no more than was charged");
        }
    }

    /** A new charge, with nothing given back. */
    static Charge of(Amount amount, Means means, String recordedBy, LocalDateTime at) {
        return new Charge(UUID.randomUUID(), amount, means, recordedBy, at, List.of());
    }

    /** What is left of it: what was charged, less what was given back. */
    public BigDecimal left() {
        return amount.value().subtract(refunded(refunds));
    }

    /** Some or all of what is left, given back. */
    Charge refund(Amount given, String reason, String by, LocalDateTime at) {
        if (given.value().compareTo(left()) > 0) {
            throw ChargeRuleViolationException.refundTooHigh(left());
        }
        List<Refund> after = new ArrayList<>(refunds);
        after.add(new Refund(UUID.randomUUID(), given, reason, by, at));
        return new Charge(id, amount, means, recordedBy, recordedAt, after);
    }

    /** Nothing left to give back of it. */
    boolean settled() {
        return left().signum() == 0;
    }

    private static BigDecimal refunded(List<Refund> refunds) {
        return refunds.stream().map(refund -> refund.amount().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
