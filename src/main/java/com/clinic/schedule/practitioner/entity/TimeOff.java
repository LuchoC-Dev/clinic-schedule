package com.clinic.schedule.practitioner.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A stretch of time the practitioner is absent: a whole day, or part of one. Reception records
 * it; the practitioner does not work during it whatever their usual week says.
 *
 * <p>A value object, stored inside its practitioner like {@link WorkingHours}. The end is
 * exclusive, as everywhere else in the agenda: time off until 13:00 leaves 13:00 free.
 */
@Embeddable
public record TimeOff(

        @Column(name = "starts_at", nullable = false)
        LocalDateTime startsAt,

        @Column(name = "ends_at", nullable = false)
        LocalDateTime endsAt) {

    public TimeOff {
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("Time off needs a start and an end");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("Time off must end after it starts: %s-%s".formatted(startsAt, endsAt));
        }
    }

    /** True when the span shares any time with this absence. */
    boolean overlaps(LocalDateTime from, LocalDateTime until) {
        return from.isBefore(endsAt) && startsAt.isBefore(until);
    }
}
