package com.clinic.schedule.reminder.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.reminder.application.port.ReminderSenderPort;
import com.clinic.schedule.reminder.application.usecase.SendRemindersUseCase;
import com.clinic.schedule.reminder.domain.ReminderMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The reminders over the real stack (D-78): the appointment module answers the booked appointments, the
 * patient module their contact and consent, and what happened is recorded and listed for reception. The
 * email goes to a sender that keeps it instead of an SMTP server. "Today" is 10 March 2026, at midnight,
 * so the appointments of the 11th to the 13th are reminded; each test looks only at its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class ReminderIT {

    /** Keeps what would have been sent. */
    @TestConfiguration
    static class KeptMail {

        static final List<ReminderMessage> SENT = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        ReminderSenderPort keptMail() {
            return SENT::add;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SendRemindersUseCase sendReminders;

    private CreatedPractitioner arias;

    @BeforeEach
    void setUp() throws Exception {
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
    }

    private String patient(String body) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void consent(String patient, String body) throws Exception {
        mockMvc.perform(put("/api/patients/" + patient + "/contact-consent")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private String book(String patient, String startsAt) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                                """.formatted(patient, arias.id(), arias.consulta(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private List<Map<String, Object>> recordedFor(String appointment) {
        return jdbc.queryForList("select outcome, reason, address_hash, starts_at from reminder "
                + "where appointment_id = ?", UUID.fromString(appointment));
    }

    private List<ReminderMessage> sentTo(String address) {
        return KeptMail.SENT.stream().filter(message -> message.recipient().address().equals(address)).toList();
    }

    private static String sha256(String address) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(address.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void anAppointmentOfTheNextDaysIsRemindedOnceAndRecordedWithoutItsAddress() throws Exception {
        String address = "luis-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";
        String luis = patient("""
                {"fullName": "Luis Perez", "email": {"address": "%s"}}
                """.formatted(address));
        consent(luis, """
                {"email": "PATIENT"}
                """);
        String appointment = book(luis, "2026-03-12T10:00:00");

        sendReminders.handle();
        sendReminders.handle();

        assertThat(sentTo(address)).singleElement().satisfies(message -> {
            assertThat(message.recipient().greeted()).isEqualTo("Luis Perez");
            assertThat(message.practitioner()).isEqualTo("Dra. Arias");
            // The link to cancel it, from the portal (D-83).
            assertThat(message.cancelLink()).startsWith("http://localhost:8080/portal/cancel?token=");
        });
        List<Map<String, Object>> recorded = recordedFor(appointment);
        assertThat(recorded).singleElement().satisfies(row -> {
            assertThat(row.get("outcome")).isEqualTo("SENT");
            String hash = (String) row.get("address_hash");
            assertThat(hash).doesNotContain(address).startsWith("$2");
            assertThat(new BCryptPasswordEncoder().matches(sha256(address), hash)).isTrue();
            assertThat(new BCryptPasswordEncoder().matches(sha256("someone@else.com"), hash)).isFalse();
        });
    }

    @Test
    void todaysAppointmentsAndThoseBeyondTheDaysAheadAreNotReminded() throws Exception {
        String address = "far-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";
        String patient = patient("""
                {"fullName": "Ana Gomez", "email": {"address": "%s"}}
                """.formatted(address));
        consent(patient, """
                {"email": "PATIENT"}
                """);
        String today = book(patient, "2026-03-10T15:00:00");
        String beyond = book(patient, "2026-03-14T10:00:00");

        sendReminders.handle();

        assertThat(recordedFor(today)).isEmpty();
        assertThat(recordedFor(beyond)).isEmpty();
        assertThat(sentTo(address)).isEmpty();
    }

    @Test
    void anAdolescentOnTheirMothersEmailWithOnlyHerConsentIsNotReminded() throws Exception {
        String address = "madre-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";
        String tomas = patient("""
                {"fullName": "Tomas Perez", "birthDate": "2012-01-01",
                 "email": {"address": "%s", "owner": {"name": "Ana Perez", "relation": "MOTHER"}}}
                """.formatted(address));
        consent(tomas, """
                {"email": "OWNER"}
                """);
        String appointment = book(tomas, "2026-03-11T10:00:00");

        sendReminders.handle();

        assertThat(sentTo(address)).isEmpty();
        assertThat(recordedFor(appointment)).singleElement().satisfies(row -> {
            assertThat(row.get("outcome")).isEqualTo("NOT_SENT");
            assertThat(row.get("reason")).isEqualTo("CONFIDENTIAL");
            assertThat(row.get("address_hash")).isNull();
        });

        // Once he agrees himself, the next day's run reminds him there.
        consent(tomas, """
                {"email": "PATIENT"}
                """);
        sendReminders.handle();

        assertThat(sentTo(address)).singleElement().satisfies(message -> {
            assertThat(message.recipient().greeted()).isEqualTo("Ana Perez");
            assertThat(message.recipient().patientName()).isEqualTo("Tomas Perez");
        });
    }

    @Test
    void aCancelledAppointmentIsNotReminded() throws Exception {
        String address = "baja-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";
        String patient = patient("""
                {"fullName": "Baja Perez", "email": {"address": "%s"}}
                """.formatted(address));
        consent(patient, """
                {"email": "PATIENT"}
                """);
        String appointment = book(patient, "2026-03-12T17:00:00");
        mockMvc.perform(post("/api/appointments/" + appointment + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"requestedBy\": \"PATIENT\"}"))
                .andExpect(status().isOk());

        sendReminders.handle();

        assertThat(recordedFor(appointment)).isEmpty();
        assertThat(sentTo(address)).isEmpty();
    }

    @Test
    void theDatabaseKeepsAReasonExactlyWhenItWasNotSent() {
        String insert = "insert into reminder (id, appointment_id, patient_id, practitioner_id, starts_at, channel, "
                + "outcome, reason, address_hash, recorded_at) values (?, ?, ?, ?, timestamp '2026-03-12 10:00:00', "
                + "'EMAIL', ?, ?, ?, timestamp '2026-03-10 10:00:00')";

        for (Object[] wrong : new Object[][] {
                {"NOT_SENT", null, null}, {"NOT_SENT", "NO_EMAIL", "$2a$10$hash"}, {"SENT", "NO_EMAIL", "$2a$10$hash"},
                {"SENT", null, null}, {"LOST", null, "$2a$10$hash"}}) {
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), wrong[0], wrong[1], wrong[2]))
                    .as("%s, %s, %s", wrong)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void aPatientUnderAnAliasIsNotNamed() throws Exception {
        String address = "nn-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";
        String unnamed = json.readTree(mockMvc.perform(post("/api/patients/unidentified"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(put("/api/patients/" + unnamed + "/details")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": {"address": "%s"}}
                                """.formatted(address)))
                .andExpect(status().isOk());
        consent(unnamed, """
                {"email": "PATIENT"}
                """);
        book(unnamed, "2026-03-12T16:00:00");

        sendReminders.handle();

        assertThat(sentTo(address)).singleElement()
                .satisfies(message -> assertThat(message.recipient().greeted()).isNull());
    }

    @Test
    void receptionReadsWhatHappenedToEachReminder() throws Exception {
        String address = "rosa-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";
        String rosa = patient("""
                {"fullName": "Rosa Lista", "email": {"address": "%s"}}
                """.formatted(address));
        String appointment = book(rosa, "2026-03-13T10:00:00");
        String earlier = book(patient("""
                {"fullName": "Temprano Lista"}
                """), "2026-03-13T08:00:00");
        sendReminders.handle();

        JsonNode found = json.readTree(mockMvc.perform(get("/api/reminders")
                        .param("from", "2026-03-13").param("to", "2026-03-13"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode entry = null;
        for (JsonNode reminder : found.get("reminders")) {
            if (reminder.get("appointmentId").asText().equals(appointment)) {
                entry = reminder;
            }
        }
        assertThat(entry).isNotNull();
        List<String> order = new ArrayList<>();
        found.get("reminders").forEach(reminder -> order.add(reminder.get("appointmentId").asText()));
        assertThat(order.indexOf(earlier)).isLessThan(order.indexOf(appointment)).isNotNegative();
        assertThat(entry.get("startsAt").asText()).isEqualTo("2026-03-13T10:00:00");
        assertThat(entry.get("patient").get("fullName").asText()).isEqualTo("Rosa Lista");
        assertThat(entry.get("practitioner").get("id").asText()).isEqualTo(arias.id().toString());
        assertThat(entry.get("channel").asText()).isEqualTo("EMAIL");
        assertThat(entry.get("outcome").asText()).isEqualTo("NOT_SENT");
        assertThat(entry.get("reason").asText()).isEqualTo("NO_CONSENT");
        assertThat(entry.get("recordedAt").asText()).isEqualTo("2026-03-10T00:00:00");
        assertThat(found.toString()).doesNotContain(address);

        mockMvc.perform(get("/reminders").param("from", "2026-03-13").param("to", "2026-03-13"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rosa Lista")))
                .andExpect(content().string(containsString("No enviado: sin consentimiento para el email")))
                .andExpect(content().string(not(containsString(address))));
        mockMvc.perform(get("/agenda")).andExpect(content().string(containsString("href=\"/reminders\"")));
    }

    @Test
    void withoutDaysTheListGoesFromTodayAsFarAsRemindersGo() throws Exception {
        JsonNode found = json.readTree(mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(found.get("from").asText()).isEqualTo("2026-03-10");
        assertThat(found.get("to").asText()).isEqualTo("2026-03-13");
        mockMvc.perform(get("/api/reminders").param("from", "2026-03-13").param("to", "2026-03-12"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/reminders").param("from", "2026-03-13").param("to", "2026-03-12"))
                .andExpect(content().string(containsString("El primer día es posterior al último.")));
    }

    @Test
    void onlyReceptionReadsThem() throws Exception {
        mockMvc.perform(get("/api/reminders").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reminders").with(user("arias").roles("PRACTITIONER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/reminders").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }
}
