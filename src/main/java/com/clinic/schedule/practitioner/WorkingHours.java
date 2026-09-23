package com.clinic.schedule.practitioner;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One stretch of work on a weekday: Monday from 09:00 to 13:00.
 *
 * <p>A value object. It carries the mapping annotations so JPA can store it as part of the
 * practitioner, like {@code IdentityDocument} in the patient module, but it needs nothing
 * from JPA to be built or tested (D-28).
 *
 * <p>The end is exclusive, as in the appointment's {@code TimeSlot}: 09:00-13:00 and
 * 13:00-18:00 meet without overlapping.
 */
@Embeddable
record WorkingHours(

        @Enumerated(EnumType.STRING)
        @Column(name = "day_of_week", nullable = false, length = 10)
        DayOfWeek day,

        @Column(name = "starts_at", nullable = false)
        LocalTime startsAt,

        @Column(name = "ends_at", nullable = false)
        LocalTime endsAt) {

    WorkingHours {
        if (day == null || startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("Working hours need a day, a start and an end");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException(
                    "Working hours must end after they start: %s %s-%s".formatted(day, startsAt, endsAt));
        }
    }

    /**
     * True when both stretches are on the same day and share time or meet end to start.
     * Meeting counts too: 09:00-13:00 and 13:00-18:00 describe a day with no break, which is
     * one stretch, 09:00-18:00.
     */
    boolean overlapsOrTouches(WorkingHours other) {
        return day == other.day
                && !startsAt.isAfter(other.endsAt)
                && !other.startsAt.isAfter(endsAt);
    }

    @Override
    public String toString() {
        return "%s %s-%s".formatted(day, startsAt, endsAt);
    }
}
