package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.clinicalrecord.domain.exception.CheckNotPendingException;
import com.clinic.schedule.clinicalrecord.domain.exception.ReasonRequiredException;

class AccessGrantPolicyTest {

    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final PractitionerId ARIAS = new PractitionerId(UUID.randomUUID());
    private static final PractitionerId ANALYST = new PractitionerId(UUID.randomUUID());
    private static final LocalDateTime PERFORMED = LocalDateTime.of(2026, 3, 10, 10, 15);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 12, 9, 0);

    private static final AccessGrantPolicy A_YEAR_WITH_A_REASON = new AccessGrantPolicy(Period.ofYears(1), true);

    private static Check pending() {
        return Check.perform(CheckId.newId(), PATIENT, ARIAS, PERFORMED, "Extracción de sangre", null);
    }

    @Test
    void aGrantIsForThePatientOfThePendingCheckApprovedAutomaticallyForTheConfiguredPeriod() {
        Check check = pending();

        AccessGrant grant = A_YEAR_WITH_A_REASON.grant(AccessGrantId.newId(), ANALYST, check, NOW, " Informar el hemograma ");

        assertThat(grant.patientId()).isEqualTo(PATIENT);
        assertThat(grant.practitionerId()).isEqualTo(ANALYST);
        assertThat(grant.checkId()).isEqualTo(check.id());
        assertThat(grant.approval()).isEqualTo(Approval.AUTOMATIC);
        assertThat(grant.reason()).contains("Informar el hemograma");
        assertThat(grant.validUntil()).isEqualTo(LocalDate.of(2027, 3, 12));
        assertThat(grant.isValidOn(LocalDate.of(2027, 3, 12))).isTrue();
        assertThat(grant.isValidOn(LocalDate.of(2027, 3, 13))).isFalse();
        assertThat(grant.isValidOn(LocalDate.of(2026, 3, 11))).isFalse();
    }

    @Test
    void onlyAPendingCheckAllowsAskingForAccess() {
        Check reported = pending().withResult(new CheckResult(ARIAS, PERFORMED.plusDays(1), "Normal."));

        assertThatThrownBy(() -> A_YEAR_WITH_A_REASON.grant(AccessGrantId.newId(), ANALYST, reported, NOW, "Informar"))
                .isInstanceOf(CheckNotPendingException.class);
    }

    @Test
    void aReasonIsRequiredOrOptionalAsConfigured() {
        assertThatThrownBy(() -> A_YEAR_WITH_A_REASON.grant(AccessGrantId.newId(), ANALYST, pending(), NOW, "  "))
                .isInstanceOf(ReasonRequiredException.class);

        AccessGrant withoutReason = new AccessGrantPolicy(Period.ofYears(1), false)
                .grant(AccessGrantId.newId(), ANALYST, pending(), NOW, null);
        assertThat(withoutReason.reason()).isEmpty();
    }

    @Test
    void aReasonIsAtMostFiveHundredCharacters() {
        assertThat(A_YEAR_WITH_A_REASON.grant(AccessGrantId.newId(), ANALYST, pending(), NOW, "a".repeat(500))
                .reason()).get().asString().hasSize(500);
        assertThatIllegalArgumentException().isThrownBy(
                () -> A_YEAR_WITH_A_REASON.grant(AccessGrantId.newId(), ANALYST, pending(), NOW, "a".repeat(501)));
    }

    @Test
    void theValidityMustBeLongerThanNothing() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AccessGrantPolicy(Period.ZERO, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new AccessGrantPolicy(null, true));
    }
}
