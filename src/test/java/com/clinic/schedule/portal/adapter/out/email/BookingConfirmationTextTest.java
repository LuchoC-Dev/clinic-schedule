package com.clinic.schedule.portal.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.BookingConfirmation;

/** The words of the email that confirms an appointment booked on the web (D-82). */
class BookingConfirmationTextTest {

    private static final BookingConfirmationText TEXT =
            new BookingConfirmationText("Consultorio Central", "Av. Siempre Viva 742", "011 4555-0000");

    @Test
    void itSaysTheDayTheTimeAndThePractitionerAndHowToCancel() {
        BookingConfirmation confirmation = new BookingConfirmation("ana@mail.com", "Ana Pérez",
                LocalDateTime.of(2026, 3, 12, 10, 30), "Dra. Arias");

        assertThat(TEXT.subject()).isEqualTo("Consultorio Central — Turno reservado");
        assertThat(TEXT.plain(confirmation))
                .startsWith("Consultorio Central\n\nHola, Ana Pérez:\n\n")
                .contains("Tu turno quedó reservado para el jueves 12 de marzo a las 10:30 con Dra. Arias.")
                .contains("Para cancelarlo o cambiarlo, llamá al 011 4555-0000 o acercate a recepción.")
                .endsWith("Consultorio Central · Av. Siempre Viva 742 · 011 4555-0000\n");
    }
}
