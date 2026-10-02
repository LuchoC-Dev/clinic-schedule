package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** What a patient reads of their appointments (D-80): those ahead, and those of the last 12 months. */
class OwnAppointmentsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);

    private static Appointment at(LocalDateTime startsAt) {
        return new Appointment(UUID.randomUUID(), UUID.randomUUID(), startsAt, "consulta", Appointment.State.BOOKED,
                null);
    }

    @Test
    void thoseAheadComeSoonestFirstAndThoseOfTheLastYearMostRecentFirst() {
        Appointment laterToday = at(NOW.plusHours(2));
        Appointment rightNow = at(NOW);
        Appointment nextMonth = at(NOW.plusMonths(1));
        Appointment earlierToday = at(NOW.minusHours(2));
        Appointment lastWeek = at(NOW.minusWeeks(1));

        OwnAppointments own = OwnAppointments.of(List.of(nextMonth, lastWeek, laterToday, earlierToday, rightNow), NOW);

        assertThat(own.upcoming()).containsExactly(rightNow, laterToday, nextMonth);
        assertThat(own.past()).containsExactly(earlierToday, lastWeek);
    }

    @Test
    void theLastTwelveMonthsStartOnTheSameDayAYearAgo() {
        Appointment aYearAgo = at(LocalDateTime.of(2025, 3, 10, 0, 0));
        Appointment theDayBefore = at(LocalDateTime.of(2025, 3, 9, 23, 59));

        assertThat(OwnAppointments.of(List.of(aYearAgo, theDayBefore), NOW).past()).containsExactly(aYearAgo);
    }
}
