package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Period;

import org.junit.jupiter.api.Test;

/** The age a practitioner reads beside the record: years, or months below two, or days below one (D-73). */
class AgeLabelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    private static String bornOn(LocalDate birthDate) {
        return AgeLabel.of(Period.between(birthDate, TODAY));
    }

    @Test
    void fromTwoTheAgeIsInYears() {
        assertThat(bornOn(LocalDate.of(1981, 3, 12))).isEqualTo("44 años");
        assertThat(bornOn(LocalDate.of(1981, 3, 10))).isEqualTo("45 años");
        assertThat(bornOn(LocalDate.of(2024, 3, 10))).isEqualTo("2 años");
    }

    @Test
    void belowTwoItIsInMonths() {
        assertThat(bornOn(LocalDate.of(2024, 3, 11))).isEqualTo("23 meses");
        assertThat(bornOn(LocalDate.of(2026, 2, 10))).isEqualTo("1 mes");
    }

    @Test
    void belowOneMonthItIsInDays() {
        assertThat(bornOn(LocalDate.of(2026, 2, 11))).isEqualTo("27 días");
        assertThat(bornOn(LocalDate.of(2026, 3, 9))).isEqualTo("1 día");
    }

    @Test
    void noDateOfBirthIsNoAge() {
        assertThat(AgeLabel.of(null)).isNull();
    }
}
