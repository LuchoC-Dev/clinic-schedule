package com.clinic.schedule.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** What is recorded of a cancellation (D-76). */
class CancellationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 9, 15);

    @Test
    void byHandItKeepsWhoWhenWhoAskedAndTheNoteTrimmed() {
        Cancellation cancellation = Cancellation.byHand(NOW, "recepcion", CancellationRequester.PATIENT,
                "  Viaja por trabajo  ");

        assertThat(cancellation.at()).isEqualTo(NOW);
        assertThat(cancellation.by()).isEqualTo("recepcion");
        assertThat(cancellation.origin()).isEqualTo(CancellationOrigin.BY_HAND);
        assertThat(cancellation.requestedBy()).isEqualTo(CancellationRequester.PATIENT);
        assertThat(cancellation.note()).isEqualTo("Viaja por trabajo");
    }

    @Test
    void aBlankNoteIsNone() {
        assertThat(Cancellation.byHand(NOW, "recepcion", CancellationRequester.PATIENT, "   ").note()).isNull();
        assertThat(Cancellation.byHand(NOW, "recepcion", CancellationRequester.PATIENT, null).note()).isNull();
    }

    @Test
    void aTimeOffCancelsOnThePracticesBehalf() {
        Cancellation cancellation = Cancellation.byTimeOff(NOW, "recepcion");

        assertThat(cancellation.origin()).isEqualTo(CancellationOrigin.TIME_OFF);
        assertThat(cancellation.requestedBy()).isEqualTo(CancellationRequester.PRACTICE);
        assertThat(cancellation.note()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new Cancellation(NOW, "recepcion",
                CancellationOrigin.TIME_OFF, CancellationRequester.PATIENT, null));
    }

    @Test
    void itNeedsWhenWhoHowAndWhoAsked() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                Cancellation.byHand(null, "recepcion", CancellationRequester.PATIENT, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                Cancellation.byHand(NOW, null, CancellationRequester.PATIENT, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                Cancellation.byHand(NOW, "  ", CancellationRequester.PATIENT, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                Cancellation.byHand(NOW, "recepcion", null, null));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Cancellation(NOW, "recepcion", null, CancellationRequester.PATIENT, null));
    }

    @Test
    void aNoteFitsItsColumn() {
        assertThat(Cancellation.byHand(NOW, "recepcion", CancellationRequester.PATIENT, "x".repeat(200)).note())
                .hasSize(200);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Cancellation.byHand(NOW, "recepcion", CancellationRequester.PATIENT, "x".repeat(201)))
                .withMessageContaining("at most 200");
    }

    /** On the web or from an email's link, the patient asked, and nobody wrote a note (D-83). */
    @Test
    void fromTheWebThePatientAsked() {
        Cancellation online = Cancellation.onTheWeb(NOW, "portal:cuenta");
        assertThat(online.by()).isEqualTo("portal:cuenta");
        assertThat(online.origin()).isEqualTo(CancellationOrigin.ONLINE);
        assertThat(online.requestedBy()).isEqualTo(CancellationRequester.PATIENT);
        assertThat(online.note()).isNull();

        Cancellation linked = Cancellation.byEmailLink(NOW);
        assertThat(linked.by()).isEqualTo(Cancellation.EMAIL_LINK_BY);
        assertThat(linked.origin()).isEqualTo(CancellationOrigin.EMAIL_LINK);
        assertThat(linked.requestedBy()).isEqualTo(CancellationRequester.PATIENT);
        assertThat(linked.at()).isEqualTo(NOW);

        assertThatIllegalArgumentException().isThrownBy(() -> new Cancellation(NOW, "portal:cuenta",
                CancellationOrigin.ONLINE, CancellationRequester.PRACTICE, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Cancellation(NOW, "email-link",
                CancellationOrigin.EMAIL_LINK, CancellationRequester.PRACTICE, null));
    }
}
