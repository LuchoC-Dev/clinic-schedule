package com.clinic.schedule.cashdesk.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What was charged for a visit (D-87): the amount, the means, who recorded it and when. It is never
 * changed nor deleted: nothing here changes it, and storage only ever inserts it.
 *
 * @param recordedBy the username of whoever recorded it
 */
public record Charge(UUID id, Amount amount, Means means, String recordedBy, LocalDateTime recordedAt) {

    public Charge {
        if (id == null || amount == null || means == null || recordedBy == null || recordedBy.isBlank()
                || recordedAt == null) {
            throw new IllegalArgumentException("A charge needs its amount, its means, who recorded it and when");
        }
    }

    /** A new charge in cash, the one means of part 1. */
    static Charge inCash(Amount amount, String recordedBy, LocalDateTime at) {
        return new Charge(UUID.randomUUID(), amount, Means.CASH, recordedBy, at);
    }
}
