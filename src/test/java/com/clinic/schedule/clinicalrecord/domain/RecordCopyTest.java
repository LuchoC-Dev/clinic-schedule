package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException;
import com.clinic.schedule.clinicalrecord.domain.exception.RecordCopyRefusedException.Reason;

class RecordCopyTest {

    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final PractitionerId ARIAS = new PractitionerId(UUID.randomUUID());
    private static final LocalDateTime ASKED = LocalDateTime.of(2026, 3, 10, 9, 30);
    private static final CopyCode CODE = new CopyCode("K7QM3XPA9TRD");

    private static RecordCopy asked(CopyDelivery delivery) {
        return RecordCopy.request(RecordCopyId.newId(), PATIENT, PATIENT.value(), "portal:cuenta", ASKED, delivery, CODE);
    }

    private static Reason refusal(Runnable step) {
        try {
            step.run();
        } catch (RecordCopyRefusedException e) {
            return e.reason();
        }
        throw new AssertionError("It was not refused");
    }

    @Test
    void aCopyIsPutTogetherAsItIsAskedForAndIsDueFortyEightHoursLater() {
        RecordCopy copy = asked(CopyDelivery.PDF);

        assertThat(copy.assembledAt()).isEqualTo(ASKED);
        assertThat(copy.assembly()).isEqualTo(1);
        assertThat(copy.isAuthenticated()).isFalse();
        assertThat(copy.deadline()).isEqualTo(LocalDateTime.of(2026, 3, 12, 9, 30));
        assertThat(copy.timeLeft(ASKED.plusHours(17))).isEqualTo(Duration.ofHours(31));
        assertThat(copy.timeLeft(ASKED.plusHours(51))).isEqualTo(Duration.ofHours(-3));
        assertThat(copy.isLate(ASKED.plusHours(48))).isFalse();
        assertThat(copy.isLate(ASKED.plusHours(48).plusMinutes(1))).isTrue();
    }

    @Test
    void puttingItTogetherAgainMovesTheTimeOfTheRecordButNotTheDeadline() {
        RecordCopy again = asked(CopyDelivery.PDF).reassembled(ASKED.plusHours(5));

        assertThat(again.assembledAt()).isEqualTo(ASKED.plusHours(5));
        assertThat(again.assembly()).isEqualTo(2);
        assertThat(again.deadline()).isEqualTo(ASKED.plusHours(48));
        assertThat(again.code()).isEqualTo(CODE);
    }

    @Test
    void itIsAuthenticatedOnceByWhoReviewedTheAssemblyThatIsThere() {
        RecordCopy copy = asked(CopyDelivery.PDF).reassembled(ASKED.plusHours(1));

        assertThat(refusal(() -> copy.authenticated(ARIAS, "arias", ASKED.plusHours(2), 1, true)))
                .isEqualTo(Reason.REASSEMBLED_SINCE);
        assertThat(refusal(() -> copy.authenticated(ARIAS, "arias", ASKED.plusHours(2), 2, false)))
                .isEqualTo(Reason.NOT_REVIEWED);

        RecordCopy authenticated = copy.authenticated(ARIAS, "arias", ASKED.plusHours(2), 2, true);
        assertThat(authenticated.authentication()).contains(
                new RecordCopy.Authentication(ARIAS, "arias", ASKED.plusHours(2)));
        assertThat(authenticated.isLate(ASKED.plusHours(60))).isFalse();
        assertThat(refusal(() -> authenticated.authenticated(ARIAS, "arias", ASKED.plusHours(3), 2, true)))
                .isEqualTo(Reason.ALREADY_AUTHENTICATED);
        assertThat(refusal(() -> authenticated.reassembled(ASKED.plusHours(3))))
                .isEqualTo(Reason.ALREADY_AUTHENTICATED);
        assertThat(copy.isAuthenticated()).isFalse();
    }

    @Test
    void oneAuthenticatedAfterTheDeadlineIsLate() {
        RecordCopy late = asked(CopyDelivery.PDF).authenticated(ARIAS, "arias", ASKED.plusHours(50), 1, true);

        assertThat(late.isLate(ASKED.plusHours(50))).isTrue();
    }

    @Test
    void onlyAnAuthenticatedPdfIsDownloaded() {
        RecordCopy pdf = asked(CopyDelivery.PDF);
        RecordCopy paper = asked(CopyDelivery.PAPER).authenticated(ARIAS, "arias", ASKED.plusHours(1), 1, true);

        assertThat(refusal(pdf::requireDownloadable)).isEqualTo(Reason.NOT_AUTHENTICATED);
        assertThat(refusal(paper::requireDownloadable)).isEqualTo(Reason.OTHER_DELIVERY);
        pdf.authenticated(ARIAS, "arias", ASKED.plusHours(1), 1, true).requireDownloadable();
    }

    @Test
    void onlyAnAuthenticatedPaperCopyIsHandedOverOnceWithTheDocumentSeen() {
        RecordCopy paper = asked(CopyDelivery.PAPER);
        assertThat(refusal(() -> paper.handedOver("recepcion", ASKED.plusHours(3), true)))
                .isEqualTo(Reason.NOT_AUTHENTICATED);
        assertThat(paper.awaitsHandover()).isFalse();

        RecordCopy authenticated = paper.authenticated(ARIAS, "arias", ASKED.plusHours(1), 1, true);
        assertThat(authenticated.awaitsHandover()).isTrue();
        assertThat(refusal(() -> authenticated.handedOver("recepcion", ASKED.plusHours(3), false)))
                .isEqualTo(Reason.DOCUMENT_NOT_SEEN);

        RecordCopy handed = authenticated.handedOver("recepcion", ASKED.plusHours(3), true);
        assertThat(handed.handover()).contains(new RecordCopy.Handover("recepcion", ASKED.plusHours(3)));
        assertThat(handed.awaitsHandover()).isFalse();
        assertThat(refusal(() -> handed.handedOver("recepcion", ASKED.plusHours(4), true)))
                .isEqualTo(Reason.ALREADY_HANDED_OVER);

        RecordCopy pdf = asked(CopyDelivery.PDF).authenticated(ARIAS, "arias", ASKED.plusHours(1), 1, true);
        assertThat(pdf.awaitsHandover()).isFalse();
        assertThat(refusal(() -> pdf.handedOver("recepcion", ASKED.plusHours(3), true)))
                .isEqualTo(Reason.OTHER_DELIVERY);
    }

    @Test
    void aClockSetBackPutsNoStepBeforeTheOneItFollows() {
        RecordCopy copy = asked(CopyDelivery.PAPER).reassembled(ASKED.minusHours(1));
        assertThat(copy.assembledAt()).isEqualTo(ASKED);

        RecordCopy authenticated = copy.authenticated(ARIAS, "arias", ASKED.minusHours(1), 2, true);
        assertThat(authenticated.authentication().orElseThrow().at()).isEqualTo(ASKED);
        assertThat(authenticated.handedOver("recepcion", ASKED.minusHours(2), true).handover().orElseThrow().at())
                .isEqualTo(ASKED);
    }

    @Test
    void aCopyReadBackIsCheckedTheSameWay() {
        RecordCopyId id = RecordCopyId.newId();
        RecordCopy.Authentication authentication = new RecordCopy.Authentication(ARIAS, "arias", ASKED.plusHours(1));
        RecordCopy.Handover handover = new RecordCopy.Handover("recepcion", ASKED.plusHours(2));

        assertThatIllegalArgumentException().isThrownBy(() -> RecordCopy.restore(id, PATIENT, PATIENT.value(),
                "portal:cuenta", ASKED, CopyDelivery.PDF, CODE, ASKED.minusMinutes(1), 1, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> RecordCopy.restore(id, PATIENT, PATIENT.value(),
                "portal:cuenta", ASKED, CopyDelivery.PDF, CODE, ASKED, 0, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> RecordCopy.restore(id, PATIENT, PATIENT.value(),
                "portal:cuenta", ASKED, CopyDelivery.PDF, CODE, ASKED.plusHours(3), 2, authentication, null));
        assertThatIllegalArgumentException().isThrownBy(() -> RecordCopy.restore(id, PATIENT, PATIENT.value(),
                "portal:cuenta", ASKED, CopyDelivery.PDF, CODE, ASKED, 1, authentication, handover));
        assertThatIllegalArgumentException().isThrownBy(() -> RecordCopy.restore(id, PATIENT, PATIENT.value(),
                "portal:cuenta", ASKED, CopyDelivery.PAPER, CODE, ASKED, 1, null, handover));
        assertThatIllegalArgumentException().isThrownBy(() -> RecordCopy.restore(id, PATIENT, PATIENT.value(),
                " ", ASKED, CopyDelivery.PAPER, CODE, ASKED, 1, null, null));
        assertThat(RecordCopy.restore(id, PATIENT, PATIENT.value(), "portal:cuenta", ASKED, CopyDelivery.PAPER, CODE,
                ASKED, 1, authentication, handover).awaitsHandover()).isFalse();
    }

    @Test
    void theCodeIsTwelveCharactersOfCrockfordsBase32ReadAsPeopleTypeIt() {
        CopyCode code = CopyCode.generate(new java.util.Random(7));

        assertThat(code.value()).hasSize(12).matches("[0-9A-HJKMNP-TV-Z]{12}");
        assertThat(code.formatted()).matches("[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}");
        assertThat(CopyCode.read(" k7qm-3xpa 9trd ")).contains(CODE);
        assertThat(CopyCode.read("K7QM-3XPA-9TRO")).contains(new CopyCode("K7QM3XPA9TR0"));
        assertThat(CopyCode.read("K7QM-3XPA-9TRI")).contains(new CopyCode("K7QM3XPA9TR1"));
        assertThat(CopyCode.read("K7QM-3XPA-9TRL")).contains(new CopyCode("K7QM3XPA9TR1"));
        assertThat(CopyCode.read("K7QM-3XPA")).isEmpty();
        assertThat(CopyCode.read("K7QM-3XPA-9TRU")).isEmpty();
        assertThat(CopyCode.read(null)).isEmpty();
        assertThatThrownBy(() -> new CopyCode("K7QM3XPA9TRU")).isInstanceOf(IllegalArgumentException.class);
    }
}
