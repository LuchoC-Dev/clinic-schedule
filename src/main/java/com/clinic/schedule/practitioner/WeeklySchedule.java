package com.clinic.schedule.practitioner;

import java.util.List;

/**
 * A practitioner's usual week. Plain Java, no JPA, no Spring (D-25): what it guards is
 * arithmetic over hours, which is best tested directly.
 *
 * <p>For now it only checks that the week makes sense. Answering whether a given slot falls
 * inside it arrives with the code that asks — the booking check in {@code appointment} — and
 * time off will be subtracted from it in its own stage (D-14).
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

    List<WorkingHours> hours() {
        return hours;
    }
}
