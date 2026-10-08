package com.clinic.schedule.portal.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

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
            "abc-123_XYZ", LocalDateTime.of(2026, 3, 13, 9, 30), List.of(), List.of());

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

    /** The children it registers with the adult, and those it could not, with nothing more said of them (D-92). */
    @Test
    void theLinkNamesTheChildrenItRegistersAndThoseItCouldNot() {
        String text = TEXT.plain(new RegistrationMessage("ana@mail.com", "Ana Pérez", "abc", MESSAGE.expiresAt(),
                List.of("Tomás Pérez", "Sofía Pérez"), List.of("Juan Pérez")));
        assertThat(text)
                .contains("Junto con vos registramos a Tomás Pérez y Sofía Pérez: desde tu cuenta vas a poder ver, "
                        + "sacar y cancelar sus turnos, uno por delante cada uno hasta que los verifiquemos.")
                .contains("lo que prueba que sos su madre, padre o tutor")
                .contains("A Juan Pérez no lo pudimos registrar con ese documento: acercate a recepción con su "
                        + "documento.")
                .doesNotContainIgnoringCase("paciente");

        assertThat(TEXT.plain(new RegistrationMessage("ana@mail.com", "Ana Pérez", "abc", MESSAGE.expiresAt(),
                List.of(), List.of("Juan Pérez", "Luz Pérez"))))
                .contains("A Juan Pérez y Luz Pérez no los pudimos registrar con esos documentos: acercate a "
                        + "recepción con sus documentos.")
                .doesNotContain("Junto con vos");
        assertThat(TEXT.plain(MESSAGE)).doesNotContain("Junto con vos").doesNotContain("no lo pudimos");
        assertThat(RegistrationText.names(List.of("Tomás", "Sofía", "Juan"))).isEqualTo("Tomás, Sofía y Juan");
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
