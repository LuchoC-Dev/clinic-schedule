package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;

/**
 * How an order ended: fulfilled by a check, or cancelled by its author with a reason (D-55). An
 * open order has none. Each outcome carries exactly what it needs, so an order cannot be cancelled
 * without a reason nor fulfilled without its check — the type says so, not a check on loose fields.
 */
public sealed interface OrderOutcome permits OrderOutcome.Fulfilled, OrderOutcome.Cancelled {

    /** The status an order with this outcome is in. */
    OrderStatus status();

    /** One check fulfilled the order. */
    record Fulfilled(CheckId check) implements OrderOutcome {

        public Fulfilled {
            if (check == null) {
                throw new IllegalArgumentException("A fulfilled order names the check that fulfilled it");
            }
        }

        @Override
        public OrderStatus status() {
            return OrderStatus.FULFILLED;
        }
    }

    /** Its author cancelled the order, then, for that reason. */
    record Cancelled(LocalDateTime at, String reason) implements OrderOutcome {

        /** As long as the column that stores it (D-38). */
        public static final int MAX_REASON_LENGTH = 500;

        public Cancelled {
            if (at == null) {
                throw new IllegalArgumentException("A cancelled order says when it was cancelled");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("An order is cancelled with a reason");
            }
            reason = reason.strip();
            if (reason.length() > MAX_REASON_LENGTH) {
                throw new IllegalArgumentException(
                        "The reason to cancel an order is at most " + MAX_REASON_LENGTH + " characters");
            }
        }

        @Override
        public OrderStatus status() {
            return OrderStatus.CANCELLED;
        }
    }
}
