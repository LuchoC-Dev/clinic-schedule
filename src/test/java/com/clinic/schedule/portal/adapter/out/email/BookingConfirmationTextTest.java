package com.clinic.schedule.portal.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.BookingConfirmation;

/** The words of the email that confirms an appointment booked on the web (D-82), and its link to cancel (D-83). */
class BookingConfirmationTextTest {

    private static final BookingConfirmationText TEXT = new BookingConfirmationText("Consultorio Central",
            "Av. Siempre Viva 742", "011 4555-0000", "https://turnos.example/");

    private static BookingConfirmation confirmation(String cancelToken) {
        return new BookingConfirmation("ana@mail.com", "Ana Pérez", LocalDateTime.of(2026, 3, 12, 10, 30),
                "Dra. Arias", cancelToken);
    }

    @Test
    void itSaysTheDayTheTimeAndThePractitionerAndLinksToCancel() {
        assertThat(TEXT.subject()).isEqualTo("Consultorio Central — Turno reservado");
        assertThat(TEXT.plain(confirmation("abc-123")))
                .startsWith("Consultorio Central\n\nHola, Ana Pérez:\n\n")
                .contains("Tu turno quedó reservado para el jueves 12 de marzo a las 10:30 con Dra. Arias.")
                .contains("Para cancelarlo, entrá a este link:\n\n"
                        + "https://turnos.example/portal/cancel?token=abc-123\n\n"
                        + "Para cambiarlo, llamá al 011 4555-0000 o acercate a recepción.\n\n")
                .endsWith("Consultorio Central · Av. Siempre Viva 742 · 011 4555-0000\n");
    }

    @Test
    void withNoTimeLeftToCancelOnTheWebItSaysToCall() {
        assertThat(TEXT.plain(confirmation(null)))
                .contains("Para cancelarlo o cambiarlo, llamá al 011 4555-0000 o acercate a recepción.\n\n")
                .doesNotContain("link");
    }
}
