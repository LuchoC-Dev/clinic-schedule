package com.clinic.schedule.portal.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.PasswordResetMessage;

/** The words of the email with the link to choose a new password (D-86). */
class PasswordResetTextTest {

    private static final PasswordResetText TEXT = new PasswordResetText("Consultorio Central",
            "Av. Siempre Viva 742", "011 4555-0000", "https://turnos.example/");

    @Test
    void itGreetsThePatientAndGivesTheLinkWithWhenItExpires() {
        assertThat(TEXT.subject()).isEqualTo("Consultorio Central — Elegí una contraseña nueva");
        assertThat(TEXT.plain(new PasswordResetMessage("ana@mail.com", "Ana Pérez", "abc-123",
                LocalDateTime.of(2026, 3, 10, 11, 5))))
                .startsWith("Consultorio Central\n\nHola, Ana Pérez:\n\n")
                .contains("Para elegir una contraseña nueva para tu cuenta, entrá a este link:\n\n"
                        + "https://turnos.example/portal/password-reset?token=abc-123\n\n")
                .contains("El link vale hasta las 11:05 y sirve una sola vez.")
                .contains("Si no lo pediste, no hagas nada: tu contraseña sigue siendo la misma.")
                .endsWith("Consultorio Central · Av. Siempre Viva 742 · 011 4555-0000\n");
    }
}
