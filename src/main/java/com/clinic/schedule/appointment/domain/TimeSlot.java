package com.clinic.schedule.appointment.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The stretch of the agenda an appointment takes up: a start instant and a duration.
 *
 * <p>This is the only notion of time the booking domain has. It knows nothing about why the
 * appointment lasts what it lasts: the duration is resolved outside and handed over already
 * calculated (D-08).
 *
 * <p>The end is exclusive, so an appointment ending at 10:30 and one starting at 10:30 do
 * not overlap. That boundary is the whole point of this class.
 */
public record TimeSlot(LocalDateTime startsAt, Duration duration) {

    public TimeSlot {
        if (startsAt == null) {
            throw new IllegalArgumentException("An appointment needs a start time");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("An appointment needs a positive duration");
        }
    }

    public static TimeSlot of(LocalDateTime startsAt, int minutes) {
        return new TimeSlot(startsAt, Duration.ofMinutes(minutes));
    }

    public LocalDateTime endsAt() {
        return startsAt.plus(duration);
    }

    public boolean overlaps(TimeSlot other) {
        return startsAt.isBefore(other.endsAt()) && other.startsAt().isBefore(endsAt());
    }
}
