package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ClinicalRecordAccessPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final ClinicalRecordAccessPolicy A_YEAR = new ClinicalRecordAccessPolicy(Period.ofYears(1));
    private static final List<AccessGrant> NO_GRANTS = List.of();

    private static Optional<LocalDateTime> lastSeenOn(LocalDate day) {
        return Optional.of(day.atTime(10, 15));
    }

    private static AccessGrant grantedOn(LocalDate day, LocalDate until) {
        return AccessGrant.restore(AccessGrantId.newId(), new PatientId(UUID.randomUUID()),
                new PractitionerId(UUID.randomUUID()), CheckId.newId(), day.atTime(12, 0), until, null,
                Approval.AUTOMATIC);
    }

    @Test
    void aPatientNeverSeenAndNotSeenTodayIsClosed() {
        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.NONE, NO_GRANTS, TODAY)).isFalse();
    }

    @Test
    void aPatientSeenTodayIsOpenEvenBeforeTheyArrive() {
        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.EXPECTED, NO_GRANTS, TODAY)).isTrue();
        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.ARRIVED, NO_GRANTS, TODAY)).isTrue();
    }

    @Test
    void theRecordStaysOpenUntilTheSameDayAYearAfterThePatientWasLastSeen() {
        assertThat(A_YEAR.mayRead(lastSeenOn(LocalDate.of(2026, 3, 10)), VisitToday.NONE, NO_GRANTS, TODAY)).isTrue();
        assertThat(A_YEAR.mayRead(lastSeenOn(LocalDate.of(2026, 3, 9)), VisitToday.NONE, NO_GRANTS, TODAY)).isFalse();
        assertThat(A_YEAR.mayRead(lastSeenOn(LocalDate.of(2026, 12, 1)), VisitToday.NONE, NO_GRANTS, TODAY)).isTrue();
    }

    @Test
    void aPatientLastSeenLongerAgoIsANewOneUntilSeenAgain() {
        Optional<LocalDateTime> tenYearsAgo = lastSeenOn(LocalDate.of(2017, 3, 10));

        assertThat(A_YEAR.mayRead(tenYearsAgo, VisitToday.NONE, NO_GRANTS, TODAY)).isFalse();
        assertThat(A_YEAR.mayRead(tenYearsAgo, VisitToday.EXPECTED, NO_GRANTS, TODAY)).isTrue();
    }

    @Test
    void thePeriodIsWhateverIsConfigured() {
        ClinicalRecordAccessPolicy sixMonths = new ClinicalRecordAccessPolicy(Period.ofMonths(6));
        Optional<LocalDateTime> tenMonthsAgo = lastSeenOn(LocalDate.of(2026, 5, 10));

        assertThat(sixMonths.mayRead(tenMonthsAgo, VisitToday.NONE, NO_GRANTS, TODAY)).isFalse();
        assertThat(A_YEAR.mayRead(tenMonthsAgo, VisitToday.NONE, NO_GRANTS, TODAY)).isTrue();
    }

    @Test
    void aValidGrantOpensTheRecordAndAnExpiredOneDoesNot() {
        AccessGrant valid = grantedOn(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 10));
        AccessGrant expired = grantedOn(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 9));

        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.NONE, List.of(valid), TODAY)).isTrue();
        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.NONE, List.of(expired), TODAY)).isFalse();
        assertThat(A_YEAR.mayRead(Optional.empty(), VisitToday.NONE, List.of(expired, valid), TODAY)).isTrue();
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
