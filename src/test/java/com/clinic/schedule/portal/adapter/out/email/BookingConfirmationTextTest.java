package com.clinic.schedule.portal.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.portal.domain.BookingConfirmation;

/**
 * The words of the email that confirms an appointment booked on the web (D-82), its link to cancel (D-83), and
 * whom it is for when booked for a member of the family (D-85); and of the one that confirms a move (D-93).
 */
class BookingConfirmationTextTest {

    private static final BookingConfirmationText TEXT = new BookingConfirmationText("Consultorio Central",
            "Av. Siempre Viva 742", "011 4555-0000", "https://turnos.example/");

    private static BookingConfirmation confirmation(String cancelToken) {
        return new BookingConfirmation("ana@mail.com", "Ana Pérez", null, LocalDateTime.of(2026, 3, 12, 10, 30),
                "Dra. Arias", cancelToken, false);
    }

    @Test
    void itSaysTheDayTheTimeAndThePractitionerAndLinksToCancel() {
        assertThat(TEXT.subject(confirmation("abc-123"))).isEqualTo("Consultorio Central — Turno reservado");
        assertThat(TEXT.plain(confirmation("abc-123")))
                .startsWith("Consultorio Central\n\nHola, Ana Pérez:\n\n")
                .contains("Tu turno quedó reservado para el jueves 12 de marzo a las 10:30 con Dra. Arias.")
                .contains("Para cancelarlo, entrá a este link:\n\n"
                        + "https://turnos.example/portal/cancel?token=abc-123\n\n"
                        + "Para cambiarlo, llamá al 011 4555-0000 o acercate a recepción.\n\n")
                .endsWith("Consultorio Central · Av. Siempre Viva 742 · 011 4555-0000\n");
    }

    @Test
    void forAMemberOfTheFamilyItNamesThemAndGreetsWhoBookedIt() {
        assertThat(TEXT.plain(new BookingConfirmation("ana@mail.com", "Ana Pérez", "Tomás Pérez",
                LocalDateTime.of(2026, 3, 12, 10, 30), "Dra. Arias", "abc-123", false)))
                .startsWith("Consultorio Central\n\nHola, Ana Pérez:\n\n")
                .contains("El turno de Tomás Pérez quedó reservado para el jueves 12 de marzo a las 10:30 con "
                        + "Dra. Arias.")
                .doesNotContain("Tu turno");
    }

    @Test
    void aMoveSaysTheNewTimeAndLinksToCancelIt() {
        BookingConfirmation moved = new BookingConfirmation("ana@mail.com", "Ana Pérez", "Tomás Pérez",
                LocalDateTime.of(2026, 3, 13, 9, 15), "Dra. Arias", "def-456", true);
        assertThat(TEXT.subject(moved)).isEqualTo("Consultorio Central — Turno cambiado");
        assertThat(TEXT.plain(moved))
                .contains("El turno de Tomás Pérez se cambió al viernes 13 de marzo a las 09:15 con Dra. Arias.")
                .contains("https://turnos.example/portal/cancel?token=def-456")
                .doesNotContain("reservado");
    }

    @Test
    void withNoTimeLeftToCancelOnTheWebItSaysToCall() {
        assertThat(TEXT.plain(confirmation(null)))
                .contains("Para cancelarlo o cambiarlo, llamá al 011 4555-0000 o acercate a recepción.\n\n")
                .doesNotContain("link");
    }
}
