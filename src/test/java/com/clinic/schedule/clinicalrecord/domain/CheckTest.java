package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.clinicalrecord.domain.exception.ResultAlreadyRecordedException;

class CheckTest {

    private static final CheckId ID = CheckId.newId();
    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final PractitionerId WHO_DID_IT = new PractitionerId(UUID.randomUUID());
    private static final PractitionerId ANALYST = new PractitionerId(UUID.randomUUID());
    private static final ConsultationId CONSULTATION = ConsultationId.newId();
    private static final LocalDateTime AT = LocalDateTime.of(2026, 3, 10, 10, 15);

    private static Check pending() {
        return Check.perform(ID, PATIENT, WHO_DID_IT, AT, "  Extracción de sangre para hemograma.  ", CONSULTATION);
    }

    @Test
    void aCheckJustDoneHasItsResultPending() {
        Check check = pending();

        assertThat(check.isPending()).isTrue();
        assertThat(check.result()).isEmpty();
        assertThat(check.description()).isEqualTo("Extracción de sangre para hemograma.");
        assertThat(check.consultationId()).contains(CONSULTATION);
    }

    @Test
    void aCheckWhoseResultIsKnownAtOnceIsBornReportedByWhoeverDidIt() {
        Check check = Check.performWithResult(ID, PATIENT, WHO_DID_IT, AT, "Pulso", CONSULTATION, " 72 lpm ");

        assertThat(check.isPending()).isFalse();
        assertThat(check.result()).get().satisfies(result -> {
            assertThat(result.author()).isEqualTo(WHO_DID_IT);
            assertThat(result.writtenAt()).isEqualTo(AT);
            assertThat(result.text()).isEqualTo("72 lpm");
        });
    }

    @Test
    void theResultMayComeLaterFromSomeoneElse() {
        Check reported = pending().withResult(new CheckResult(ANALYST, AT.plusDays(2), "Hemoglobina 13,5 g/dl"));

        assertThat(reported.isPending()).isFalse();
        assertThat(reported.result()).get().extracting(CheckResult::author).isEqualTo(ANALYST);
        assertThat(reported.performedBy()).isEqualTo(WHO_DID_IT);
        assertThat(reported.description()).isEqualTo(pending().description());
    }

    @Test
    void aCheckHasOneResultAndItNeverChanges() {
        Check reported = pending().withResult(new CheckResult(ANALYST, AT.plusDays(2), "Primero."));

        assertThatThrownBy(() -> reported.withResult(new CheckResult(ANALYST, AT.plusDays(3), "Otro.")))
                .isInstanceOf(ResultAlreadyRecordedException.class);
    }

    @Test
    void aResultIsWrittenAfterTheCheckWasDone() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> pending().withResult(new CheckResult(ANALYST, AT.minusMinutes(1), "Antes.")));
    }

    @Test
    void aCheckWithoutAConsultationIsAllowed() {
        Check emergency = Check.perform(ID, PATIENT, WHO_DID_IT, AT, "Electrocardiograma", null);

        assertThat(emergency.consultationId()).isEmpty();
    }

    @Test
    void aCheckNeedsWhatWhoWhenAndADescriptionOfAtMostTwoThousandCharacters() {
        assertThatIllegalArgumentException().isThrownBy(() -> Check.perform(null, PATIENT, WHO_DID_IT, AT, "x", null));
        assertThatIllegalArgumentException().isThrownBy(() -> Check.perform(ID, null, WHO_DID_IT, AT, "x", null));
        assertThatIllegalArgumentException().isThrownBy(() -> Check.perform(ID, PATIENT, null, AT, "x", null));
        assertThatIllegalArgumentException().isThrownBy(() -> Check.perform(ID, PATIENT, WHO_DID_IT, null, "x", null));
        assertThatIllegalArgumentException().isThrownBy(() -> Check.perform(ID, PATIENT, WHO_DID_IT, AT, "  ", null));
        assertThat(Check.perform(ID, PATIENT, WHO_DID_IT, AT, "a".repeat(2_000), null).description()).hasSize(2_000);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Check.perform(ID, PATIENT, WHO_DID_IT, AT, "a".repeat(2_001), null));
    }

    @Test
    void aResultNeedsAnAuthorATimeAndATextOfAtMostTenThousandCharacters() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CheckResult(null, AT, "x"));
        assertThatIllegalArgumentException().isThrownBy(() -> new CheckResult(ANALYST, null, "x"));
        assertThat(new CheckResult(ANALYST, AT, "a".repeat(10_000)).text()).hasSize(10_000);
        assertThatIllegalArgumentException().isThrownBy(() -> new CheckResult(ANALYST, AT, "a".repeat(10_001)));
    }

    @Test
    void aStoredCheckIsCheckedWithItsResult() {
        CheckResult early = new CheckResult(ANALYST, AT.minusDays(1), "Antes de hacerlo.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Check.restore(ID, PATIENT, WHO_DID_IT, AT, "x", null, early));
        assertThat(Check.restore(ID, PATIENT, WHO_DID_IT, AT, "x", null, null).isPending()).isTrue();
    }

    @Test
    void aResultMayBeOnlyTheChecksAttachmentsButNeedsOneThen() {
        CheckResult withoutText = new CheckResult(ANALYST, AT.plusDays(1), "  ");

        assertThat(withoutText.hasText()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> pending().withResult(withoutText));
        assertThatIllegalArgumentException().isThrownBy(() -> pending().withResult(withoutText, false));
        assertThat(pending().withResult(withoutText, true).isPending()).isFalse();
        assertThat(pending().withResult(new CheckResult(ANALYST, AT.plusDays(1), "Normal."), false).isPending())
                .isFalse();
    }
}
