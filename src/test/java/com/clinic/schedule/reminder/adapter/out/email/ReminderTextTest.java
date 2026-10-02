package com.clinic.schedule.reminder.adapter.out.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.reminder.domain.Recipient;
import com.clinic.schedule.reminder.domain.ReminderMessage;

/** What a reminder says, in Spanish, to whom it goes (D-78). */
class ReminderTextTest {

    private static final LocalDateTime THURSDAY = LocalDateTime.of(2026, 3, 12, 10, 30);
    private final ReminderText text = new ReminderText("Consultorio Norte", "Av. Siempre Viva 742", "011 4555-0000");

    private String plain(Recipient recipient) {
        return text.plain(new ReminderMessage(recipient, THURSDAY, "Dra. Laura Arias"));
    }

    @Test
    void itGreetsThePatientAndSaysWhenWithWhomAndHowToCancel() {
        String reminder = plain(new Recipient("luis@mail.com", "Luis Pérez", false, null));

        assertThat(text.subject()).isEqualTo("Recordatorio de turno — Consultorio Norte");
        assertThat(reminder).isEqualTo("""
                Consultorio Norte

                Estimado/a Luis Pérez:

                Le recordamos su turno del jueves 12/03/2026 a las 10:30 con Dra. Laura Arias.

                Para cancelarlo o cambiarlo, llame al 011 4555-0000 o acérquese a recepción, en Av. Siempre Viva 742.
                """);
    }

    @Test
    void toAResponsiblePersonItSaysWhoseAppointmentItIs() {
        assertThat(plain(new Recipient("ana@mail.com", "Ana Pérez", true, "Tomás Pérez")))
                .contains("Estimado/a Ana Pérez:")
                .contains("Le recordamos el turno de Tomás Pérez del jueves 12/03/2026");
    }

    @Test
    void aPatientUnderAnAliasIsNamedNowhereNorGreetedAsAManOrAWoman() {
        assertThat(plain(new Recipient("nn@mail.com", null, false, null)))
                .contains("Hola:\n")
                .doesNotContain("Estimado")
                .contains("Le recordamos su turno");
        assertThat(plain(new Recipient("ana@mail.com", "Ana Pérez", true, null)))
                .contains("Le recordamos un turno del");
    }

    @Test
    void theHtmlSaysTheSameEscaped() {
        String html = text.html(new ReminderMessage(new Recipient("a@mail.com", "Ana <b>", false, null), THURSDAY,
                "Dra. Laura Arias"));

        assertThat(html).contains("<h2>Consultorio Norte</h2>")
                .contains("Estimado/a Ana &lt;b&gt;:")
                .contains("Le recordamos su turno del jueves 12/03/2026 a las 10:30 con Dra. Laura Arias.")
                .contains("acérquese");
    }
}
