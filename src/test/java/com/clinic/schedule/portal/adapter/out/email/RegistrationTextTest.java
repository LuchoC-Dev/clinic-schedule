package com.clinic.schedule.portal.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.RegistrationMessage;
import com.clinic.schedule.portal.domain.RegistrationRefusedMessage;

/**
 * The words of the emails that answer a registration on the web (D-91): the link to confirm it, or the request to
 * come to reception, which tells nothing of who is a patient.
 */
class RegistrationTextTest {

    private static final RegistrationText TEXT = new RegistrationText("Consultorio Central", "Av. Siempre Viva 742",
            "011 4555-0000", "https://turnos.example/");
    private static final RegistrationMessage MESSAGE = new RegistrationMessage("ana@mail.com", "Ana Pérez",
            "abc-123_XYZ", LocalDateTime.of(2026, 3, 13, 9, 30));

    @Test
    void theLinkCarriesTheTokenAloneUnderTheAddressPatientsReach() {
        assertThat(TEXT.link(MESSAGE)).isEqualTo("https://turnos.example/portal/register/confirm?token=abc-123_XYZ");
    }

    @Test
    void theLinkToConfirmSaysWhenItExpiresAndToBringTheDocument() {
        assertThat(TEXT.subject()).isEqualTo("Consultorio Central — Tu registro en la web");
        assertThat(TEXT.plain(MESSAGE))
                .startsWith("Consultorio Central\n\nHola, Ana Pérez:\n\n")
                .contains("\n\nhttps://turnos.example/portal/register/confirm?token=abc-123_XYZ\n\n")
                .contains("El link vence el viernes 13 de marzo a las 09:30. Si venció, registrate de nuevo.")
                .contains("traé tu documento")
                .contains("un turno por delante")
                .endsWith("Consultorio Central · Av. Siempre Viva 742 · 011 4555-0000\n");
    }

    @Test
    void aKnownDocumentIsSentToReceptionWithoutSayingWhoIsAPatient() {
        String text = TEXT.plain(new RegistrationRefusedMessage("ana@mail.com", "Ana Pérez"));

        assertThat(text)
                .startsWith("Consultorio Central\n\nHola, Ana Pérez:\n\n")
                .contains("Acercate a recepción con tu documento, o llamanos al 011 4555-0000")
                .doesNotContain("http")
                .doesNotContainIgnoringCase("paciente")
                .doesNotContainIgnoringCase("cuenta")
                .endsWith("Consultorio Central · Av. Siempre Viva 742 · 011 4555-0000\n");
    }
}
