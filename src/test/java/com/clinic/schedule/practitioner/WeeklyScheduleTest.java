package com.clinic.schedule.practitioner;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The rules of a week, on their own: no Spring, no database (D-25). */
class WeeklyScheduleTest {

    private static WorkingHours hours(java.time.DayOfWeek day, String from, String to) {
        return new WorkingHours(day, LocalTime.parse(from), LocalTime.parse(to));
    }

    @Test
    void stretchesMustEndAfterTheyStart() {
        assertThatIllegalArgumentException().isThrownBy(() -> hours(MONDAY, "13:00", "09:00"));
        assertThatIllegalArgumentException().isThrownBy(() -> hours(MONDAY, "09:00", "09:00"));
    }

    @Test
    void aStretchNeedsAllItsParts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkingHours(null, LocalTime.NOON, LocalTime.MIDNIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkingHours(MONDAY, null, LocalTime.NOON));
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkingHours(MONDAY, LocalTime.NOON, null));
    }

    @Test
    void aBreakIsTheGapBetweenTwoStretchesOfTheSameDay() {
        assertThatCode(() -> new WeeklySchedule(List.of(
                hours(MONDAY, "09:00", "13:00"),
                hours(MONDAY, "14:00", "18:00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void overlappingStretchesOfTheSameDayAreRejected() {
        assertThatThrownBy(() -> new WeeklySchedule(List.of(
                hours(MONDAY, "09:00", "13:00"),
                hours(MONDAY, "12:00", "18:00"))))
                .isInstanceOf(InvalidWeeklyScheduleException.class)
                .hasMessageContaining("MONDAY 09:00-13:00")
                .hasMessageContaining("MONDAY 12:00-18:00");
    }

    @Test
    void stretchesThatMeetAreRejectedBecauseTheyAreOneStretch() {
        // 09:00-13:00 plus 13:00-18:00 is a day with no break: it has to be written as
        // 09:00-18:00, or the gap that means "break" would be ambiguous.
        assertThatThrownBy(() -> new WeeklySchedule(List.of(
                hours(MONDAY, "09:00", "13:00"),
                hours(MONDAY, "13:00", "18:00"))))
                .isInstanceOf(InvalidWeeklyScheduleException.class);
    }

    @Test
    void aStretchContainedInAnotherIsRejected() {
        assertThatThrownBy(() -> new WeeklySchedule(List.of(
                hours(MONDAY, "09:00", "18:00"),
                hours(MONDAY, "10:00", "11:00"))))
                .isInstanceOf(InvalidWeeklyScheduleException.class);
    }

    @Test
    void theSameStretchTwiceIsRejected() {
        assertThatThrownBy(() -> new WeeklySchedule(List.of(
                hours(MONDAY, "09:00", "13:00"),
                hours(MONDAY, "09:00", "13:00"))))
                .isInstanceOf(InvalidWeeklyScheduleException.class);
    }

    @Test
    void theSameHoursOnDifferentDaysAreFine() {
        assertThatCode(() -> new WeeklySchedule(List.of(
                hours(MONDAY, "09:00", "13:00"),
                hours(TUESDAY, "09:00", "13:00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void anEmptyWeekIsValid() {
        assertThat(new WeeklySchedule(List.of()).hours()).isEmpty();
    }
}
