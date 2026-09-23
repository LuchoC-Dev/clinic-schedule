package com.clinic.schedule.practitioner;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * A practitioner's usual week. Plain Java, no JPA, no Spring (D-25): what it guards is
 * arithmetic over hours, which is best tested directly.
 *
 * <p>Time off is not subtracted here yet; it arrives in its own stage, and with it the
 * policy that combines both (D-28).
 */
final class WeeklySchedule {

    private final List<WorkingHours> hours;

    /**
     * @throws InvalidWeeklyScheduleException when two stretches of the same day overlap or
     *                                        meet; see {@link WorkingHours#overlapsOrTouches}
     */
    WeeklySchedule(List<WorkingHours> hours) {
        for (int i = 0; i < hours.size(); i++) {
            for (int j = i + 1; j < hours.size(); j++) {
                if (hours.get(i).overlapsOrTouches(hours.get(j))) {
                    throw new InvalidWeeklyScheduleException(hours.get(i), hours.get(j));
                }
            }
        }
        this.hours = List.copyOf(hours);
    }

    /**
     * True when the whole span falls inside a single stretch of that weekday.
     *
     * <p>A span that crosses a break does not fit, even if both ends are inside working
     * hours: 12:30-13:30 does not fit a Monday of 09:00-13:00 and 14:00-18:00. Nor does one
     * that crosses midnight, since no stretch does.
     */
    boolean covers(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (!startsAt.toLocalDate().equals(endsAt.toLocalDate())) {
            return false;
        }
        DayOfWeek day = startsAt.getDayOfWeek();
        LocalTime from = startsAt.toLocalTime();
        LocalTime to = endsAt.toLocalTime();
        return hours.stream().anyMatch(stretch -> stretch.day() == day && stretch.contains(from, to));
    }

    List<WorkingHours> hours() {
        return hours;
    }
}
