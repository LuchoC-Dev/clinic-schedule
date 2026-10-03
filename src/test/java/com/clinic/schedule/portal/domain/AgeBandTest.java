package com.clinic.schedule.portal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** A child until the day before turning 13, an adolescent until the day before turning 18 (D-84). */
class AgeBandTest {

    private static final LocalDate BORN = LocalDate.of(2010, 6, 15);

    @Test
    void thirteenAndEighteenAreCountedOnTheBirthday() {
        assertThat(AgeBand.of(BORN, LocalDate.of(2023, 6, 14))).isEqualTo(AgeBand.CHILD);
        assertThat(AgeBand.of(BORN, LocalDate.of(2023, 6, 15))).isEqualTo(AgeBand.ADOLESCENT);
        assertThat(AgeBand.of(BORN, LocalDate.of(2028, 6, 14))).isEqualTo(AgeBand.ADOLESCENT);
        assertThat(AgeBand.of(BORN, LocalDate.of(2028, 6, 15))).isEqualTo(AgeBand.ADULT);
    }
}
