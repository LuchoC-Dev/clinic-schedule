package com.clinic.schedule.practitioner.entity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Whether a practitioner is available for a span: inside their usual week, and not during any
 * time off. Plain Java, no JPA, no Spring (D-25).
 *
 * <p>It arrives with time off, the second thing to combine with the week (D-28). Before that,
 * the week alone answered, and a class combining one thing would have been scaffolding.
 */
final class AvailabilityPolicy {

    private final WeeklySchedule week;
    private final List<TimeOff> timeOff;

    AvailabilityPolicy(WeeklySchedule week, Collection<TimeOff> timeOff) {
        this.week = week;
        this.timeOff = List.copyOf(timeOff);
    }

    boolean isAvailable(LocalDateTime startsAt, LocalDateTime endsAt) {
        return week.covers(startsAt, endsAt)
                && timeOff.stream().noneMatch(absence -> absence.overlaps(startsAt, endsAt));
    }
}
