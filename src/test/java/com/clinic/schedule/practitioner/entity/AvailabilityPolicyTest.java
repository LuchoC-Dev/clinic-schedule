package com.clinic.schedule.practitioner.entity;

import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The week minus the time off, on its own: no Spring, no database (D-25). */
class AvailabilityPolicyTest {

    /** Tuesday 2026-03-10. */
    private static final LocalDate TUESDAY_10 = LocalDate.of(2026, 3, 10);

    private static final WeeklySchedule TUESDAYS_NINE_TO_SIX = new WeeklySchedule(List.of(
            new WorkingHours(TUESDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))));

    private static LocalDateTime at(String time) {
        return TUESDAY_10.atTime(LocalTime.parse(time));
    }

    private static AvailabilityPolicy withTimeOff(TimeOff... absences) {
        return new AvailabilityPolicy(TUESDAYS_NINE_TO_SIX, List.of(absences));
    }

    @Test
    void withoutTimeOffTheWeekAloneDecides() {
        AvailabilityPolicy policy = withTimeOff();

        assertThat(policy.isAvailable(at("10:00"), at("10:30"))).isTrue();
        assertThat(policy.isAvailable(at("19:00"), at("19:30"))).isFalse();
    }

    @Test
    void aWholeDayOffBlocksEveryWorkingHour() {
        AvailabilityPolicy policy = withTimeOff(new TimeOff(TUESDAY_10.atStartOfDay(), TUESDAY_10.plusDays(1).atStartOfDay()));

        assertThat(policy.isAvailable(at("09:00"), at("09:30"))).isFalse();
        assertThat(policy.isAvailable(at("17:30"), at("18:00"))).isFalse();
    }

    @Test
    void aWholeDayOffLeavesTheNextWeekAlone() {
        AvailabilityPolicy policy = withTimeOff(new TimeOff(TUESDAY_10.atStartOfDay(), TUESDAY_10.plusDays(1).atStartOfDay()));

        LocalDateTime nextTuesday = at("10:00").plusWeeks(1);
        assertThat(policy.isAvailable(nextTuesday, nextTuesday.plusMinutes(30))).isTrue();
    }

    @Test
    void partOfADayOffBlocksOnlyWhatItTouches() {
        AvailabilityPolicy policy = withTimeOff(new TimeOff(at("11:00"), at("13:00")));

        assertThat(policy.isAvailable(at("10:00"), at("10:30"))).isTrue();
        assertThat(policy.isAvailable(at("10:45"), at("11:15"))).isFalse();
        assertThat(policy.isAvailable(at("12:00"), at("12:30"))).isFalse();
        assertThat(policy.isAvailable(at("14:00"), at("14:30"))).isTrue();
    }

    @Test
    void theEndsOfTheTimeOffAreFree() {
        AvailabilityPolicy policy = withTimeOff(new TimeOff(at("11:00"), at("13:00")));

        assertThat(policy.isAvailable(at("10:30"), at("11:00"))).isTrue();
        assertThat(policy.isAvailable(at("13:00"), at("13:30"))).isTrue();
    }

    @Test
    void timeOffMustEndAfterItStarts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeOff(at("13:00"), at("11:00")));
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeOff(at("13:00"), at("13:00")));
    }
}
