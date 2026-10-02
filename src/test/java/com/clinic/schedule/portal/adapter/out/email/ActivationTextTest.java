package com.clinic.schedule.portal.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.ActivationMessage;

/** The words of the activation email, which the patient reads (D-79), and the link in it. */
class ActivationTextTest {

    private static final ActivationText TEXT = new ActivationText("Consultorio Central", "Av. Siempre Viva 742",
            "011 4555-0000", "https://turnos.example/");
    private static final ActivationMessage MESSAGE = new ActivationMessage("ana@mail.com", "Ana Pérez", "abc-123_XYZ",
            LocalDateTime.of(2026, 3, 13, 9, 30));

    @Test
    void theLinkCarriesTheTokenAloneUnderTheAddressPatientsReach() {
        assertThat(TEXT.link(MESSAGE)).isEqualTo("https://turnos.example/portal/activation?token=abc-123_XYZ");
        assertThat(new ActivationText("C", "A", "P", "http://localhost:8080").link(MESSAGE))
                .isEqualTo("http://localhost:8080/portal/activation?token=abc-123_XYZ");
    }

    @Test
    void itGreetsThePatientAndSaysWhatTheLinkIsForAndWhenItExpires() {
        assertThat(TEXT.subject()).isEqualTo("Consultorio Central — Activá tu cuenta");
        assertThat(TEXT.plain(MESSAGE))
                .startsWith("Consultorio Central\n\nHola, Ana Pérez:\n\n")
                .contains("para ver tus turnos por la web")
                .contains("\n\nhttps://turnos.example/portal/activation?token=abc-123_XYZ\n\n")
                .contains("El link vence el viernes 13 de marzo a las 09:30. Si venció, pedí otro en recepción.")
                .contains("Si no pediste esta cuenta, avisanos al 011 4555-0000.")
                .endsWith("Consultorio Central · Av. Siempre Viva 742 · 011 4555-0000\n");
    }
}
