package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClinicalRecordAccessPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final ClinicalRecordAccessPolicy A_YEAR = new ClinicalRecordAccessPolicy(Period.ofYears(1));

    private static Optional<LocalDateTime> lastConsultationOn(LocalDate day) {
        return Optional.of(day.atTime(10, 15));
    }

    @Test
    void aPatientNeverSeenAndNotSeenTodayIsClosed() {
        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.NONE, TODAY)).isFalse();
    }

    @Test
    void aPatientSeenTodayIsOpenEvenBeforeTheyArrive() {
        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.EXPECTED, TODAY)).isTrue();
        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.ARRIVED, TODAY)).isTrue();
    }

    @Test
    void theRecordStaysOpenUntilTheSameDayAYearAfterTheLastConsultation() {
        assertThat(A_YEAR.mayRead(lastConsultationOn(LocalDate.of(2026, 3, 10)), VisitToday.NONE, TODAY)).isTrue();
        assertThat(A_YEAR.mayRead(lastConsultationOn(LocalDate.of(2026, 3, 9)), VisitToday.NONE, TODAY)).isFalse();
        assertThat(A_YEAR.mayRead(lastConsultationOn(LocalDate.of(2026, 12, 1)), VisitToday.NONE, TODAY)).isTrue();
    }

    @Test
    void aPatientLastSeenLongerAgoIsANewOneUntilSeenAgain() {
        Optional<LocalDateTime> tenYearsAgo = lastConsultationOn(LocalDate.of(2017, 3, 10));

        assertThat(A_YEAR.mayRead(tenYearsAgo, VisitToday.NONE, TODAY)).isFalse();
        assertThat(A_YEAR.mayRead(tenYearsAgo, VisitToday.EXPECTED, TODAY)).isTrue();
    }

    @Test
    void thePeriodIsWhateverIsConfigured() {
        ClinicalRecordAccessPolicy sixMonths = new ClinicalRecordAccessPolicy(Period.ofMonths(6));
        Optional<LocalDateTime> tenMonthsAgo = lastConsultationOn(LocalDate.of(2026, 5, 10));

        assertThat(sixMonths.mayRead(tenMonthsAgo, VisitToday.NONE, TODAY)).isFalse();
        assertThat(A_YEAR.mayRead(tenMonthsAgo, VisitToday.NONE, TODAY)).isTrue();
    }

    @Test
    void thePeriodMustBeLongerThanNothing() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClinicalRecordAccessPolicy(Period.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClinicalRecordAccessPolicy(Period.ofDays(-1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new ClinicalRecordAccessPolicy(null));
    }

    @Test
    void aConsultationIsRecordedOnlyOnceThePatientArrived() {
        assertThat(A_YEAR.mayRecord(VisitToday.ARRIVED)).isTrue();
        assertThat(A_YEAR.mayRecord(VisitToday.EXPECTED)).isFalse();
        assertThat(A_YEAR.mayRecord(VisitToday.NONE)).isFalse();
    }
}
