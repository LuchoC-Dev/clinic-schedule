package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static com.clinic.schedule.portal.PatientAccountFixture.lastTokenTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.clinic.schedule.portal.domain.ActivationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The patients' web accounts over the real stack (D-79, D-80): reception opens one, the patient activates it
 * from the email's link and logs in with their document to read their appointments. The email goes to
 * {@link KeptAccountMail}. "Today" is 10 March 2026, at midnight.
 *
 * <p>Reception is the mock user the practitioners' fixture needs; a patient logs in with their document over
 * basic authentication, which takes its place.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class PatientAccountIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private PatientAccountFixture accounts;
    private CreatedPractitioner arias;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new PatientAccountFixture(mockMvc, json);
        arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
    }

    private static RequestPostProcessor as(Patient patient) {
        return httpBasic("DNI " + patient.dni(), PASSWORD);
    }

    private String book(Patient patient, String startsAt) throws Exception {
        return read(mockMvc.perform(post("/api/appointments").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s",
                                 "note": "Nota de recepcion"}
                                """.formatted(patient.id(), arias.id(), arias.consulta(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void activation(String token, String password, int status) throws Exception {
        mockMvc.perform(post("/api/portal/activation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().is(status));
    }

    /** Whether the link still activates, as the patients' application asks before its form (D-110). */
    private void linkChecked(String token, int status) throws Exception {
        ResultActions answer = mockMvc.perform(get("/api/portal/activation/" + token)).andExpect(status().is(status));
        if (status == 400) {
            answer.andExpect(jsonPath("$.code").value("INVALID_ACTIVATION_LINK"));
        }
    }

    private JsonNode changes(String accountId) throws Exception {
        return read(mockMvc.perform(get("/api/patient-accounts/" + accountId + "/changes").with(RECEPTION))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }

    @Test
    void receptionOpensAnAccountAndThePatientActivatesItFromTheEmail() throws Exception {
        Patient patient = accounts.adult();
        String address = address();

        JsonNode account = accounts.open(patient, "  " + address.toUpperCase() + " ");

        assertThat(account.get("status").asText()).isEqualTo("PENDING");
        assertThat(account.get("email").asText()).isEqualTo(address);
        assertThat(account.get("patientId").asText()).isEqualTo(patient.id());
        assertThat(account.get("activationSent").asBoolean()).isTrue();
        assertThat(account.has("passwordHash")).isFalse();
        ActivationMessage sent = KeptAccountMail.SENT.stream().filter(message -> message.address().equals(address))
                .findFirst().orElseThrow();
        assertThat(sent.fullName()).isEqualTo("Ana Pérez");
        assertThat(sent.expiresAt()).isEqualTo("2026-03-13T00:00:00");

        mockMvc.perform(get("/api/portal/appointments").with(as(patient))).andExpect(status().isUnauthorized());
        accounts.activate(sent.token());
        mockMvc.perform(get("/api/portal/appointments").with(as(patient))).andExpect(status().isOk());

        mockMvc.perform(get("/api/patient-accounts").with(RECEPTION).param("patientId", patient.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activationSent").doesNotExist());
        JsonNode changes = changes(account.get("id").asText());
        assertThat(changes.findValuesAsText("action")).containsExactlyInAnyOrder("OPENED", "ACTIVATION_SENT",
                "ACTIVATED");
        assertThat(changes).allSatisfy(change -> assertThat(change.get("by").isNull())
                .isEqualTo(change.get("action").asText().equals("ACTIVATED")));
    }

    @Test
    void anActivationLinkWorksOnceAndUntilItExpires() throws Exception {
        Patient patient = accounts.adult();
        String address = address();
        String accountId = accounts.open(patient, address).get("id").asText();
        String token = lastTokenTo(address);
        linkChecked(token, 204);
        linkChecked("otro", 400);

        jdbc.update("update patient_account_activation set expires_at = cast('2026-03-10 00:00:00' as timestamp) "
                + "where account_id = ?", UUID.fromString(accountId));
        linkChecked(token, 400);
        activation(token, PASSWORD, 400);

        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/activation-resend").with(RECEPTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activationSent").value(true));
        String again = lastTokenTo(address);
        assertThat(again).isNotEqualTo(token);
        activation(again, "corta", 400);
        activation(again, PASSWORD, 204);
        assertThat(jdbc.queryForObject("select count(*) from patient_account_activation where account_id = ?",
                Integer.class, UUID.fromString(accountId))).isZero();
        linkChecked(again, 400);
        activation(again, PASSWORD, 400);
        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/activation-resend").with(RECEPTION))
                .andExpect(status().isConflict());
    }

    @Test
    void aNewEmailForAPendingAccountGetsTheActivationAndTheOldLinkStopsWorking() throws Exception {
        Patient patient = accounts.adult();
        String wrong = address();
        String accountId = accounts.open(patient, wrong).get("id").asText();
        String oldToken = lastTokenTo(wrong);
        String right = address();

        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/email-change").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"" + right + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(right))
                .andExpect(jsonPath("$.activationSent").value(true));

        activation(oldToken, PASSWORD, 400);
        activation(lastTokenTo(right), PASSWORD, 204);
        assertThat(changes(accountId).findValuesAsText("action")).contains("EMAIL_CHANGED");
    }

    @Test
    void anActiveAccountsNewEmailHoldsAtOnceAndSendsNothing() throws Exception {
        Patient patient = accounts.withAccount();
        String accountId = accounts.accountOf(patient);
        String other = address();

        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/email-change").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"" + other + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(other))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.activationSent").value(false));
        assertThat(KeptAccountMail.SENT).noneMatch(message -> message.address().equals(other));
    }

    @Test
    void anAccountOpensEvenWhenItsEmailDoesNotGoOut() throws Exception {
        JsonNode account = accounts.open(accounts.adult(), "falla-" + address());

        assertThat(account.get("activationSent").asBoolean()).isFalse();
        assertThat(account.get("status").asText()).isEqualTo("PENDING");
        mockMvc.perform(post("/api/patient-accounts/" + account.get("id").asText() + "/activation-resend")
                        .with(RECEPTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activationSent").value(false));
        assertThat(changes(account.get("id").asText()).findValuesAsText("action")).containsExactly("OPENED");
    }

    @Test
    void onlyAVerifiedPatientOfThirteenOrOlderGetsOneAccount() throws Exception {
        Patient adult = accounts.adult();
        accounts.open(adult, address());

        for (String patientId : List.of(adult.id(), accounts.patient(false, "1990-05-14").id(),
                accounts.patient(true, null).id(), accounts.patient(true, "2013-03-11").id())) {
            mockMvc.perform(post("/api/patient-accounts").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"patientId\": \"" + patientId + "\", \"email\": \"" + address() + "\"}"))
                    .andExpect(status().isConflict());
        }
        mockMvc.perform(post("/api/patient-accounts").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"" + UUID.randomUUID() + "\", \"email\": \"" + address() + "\"}"))
                .andExpect(status().isBadRequest());
        String thirteenToday = accounts.patient(true, "2013-03-10").id();
        mockMvc.perform(post("/api/patient-accounts").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"" + thirteenToday + "\", \"email\": \"ana\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/patient-accounts").with(RECEPTION).param("patientId", thirteenToday))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/patient-accounts/" + UUID.randomUUID()).with(RECEPTION))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/patient-accounts/" + UUID.randomUUID() + "/changes").with(RECEPTION))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPatientReadsTheirAppointmentsWithoutTheNoteNorWhoCancelled() throws Exception {
        Patient patient = accounts.withAccount();
        String booked = book(patient, "2026-03-12T10:00:00");
        String cancelled = book(patient, "2026-03-13T10:00:00");
        mockMvc.perform(post("/api/appointments/" + cancelled + "/cancel").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedBy\": \"PATIENT\", \"note\": \"Esta internada\"}"))
                .andExpect(status().isOk());
        book(accounts.withAccount(), "2026-03-12T11:00:00");

        mockMvc.perform(get("/api/portal/appointments").with(as(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(2))
                .andExpect(jsonPath("$.upcoming[0].id").value(booked))
                .andExpect(jsonPath("$.upcoming[0].startsAt").value("2026-03-12T10:00:00"))
                .andExpect(jsonPath("$.upcoming[0].reason").value("consulta"))
                .andExpect(jsonPath("$.upcoming[0].state").value("BOOKED"))
                .andExpect(jsonPath("$.upcoming[0].practitioner.id").value(arias.id().toString()))
                .andExpect(jsonPath("$.upcoming[0].practitioner.fullName").value("Dra. Arias"))
                .andExpect(jsonPath("$.upcoming[0].cancellationRequestedBy").isEmpty())
                .andExpect(jsonPath("$.upcoming[1].id").value(cancelled))
                .andExpect(jsonPath("$.upcoming[1].state").value("CANCELLED"))
                .andExpect(jsonPath("$.upcoming[1].cancellationRequestedBy").value("PATIENT"))
                .andExpect(jsonPath("$.past.length()").value(0))
                .andExpect(content().string(not(containsString("Nota de recepcion"))))
                .andExpect(content().string(not(containsString("Esta internada"))))
                .andExpect(content().string(not(containsString("recepcion"))));
    }

    @Test
    void aDeactivatedAccountCannotLogInUntilItIsReactivated() throws Exception {
        Patient patient = accounts.withAccount();
        String accountId = accounts.accountOf(patient);

        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/deactivate").with(RECEPTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        mockMvc.perform(get("/api/portal/appointments").with(as(patient))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/deactivate").with(RECEPTION))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/reactivate").with(RECEPTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/portal/appointments").with(as(patient))).andExpect(status().isOk());
        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/reactivate").with(RECEPTION))
                .andExpect(status().isConflict());
        assertThat(changes(accountId).findValuesAsText("by")).contains("recepcion");
    }

    @Test
    void deactivatingAPendingAccountEndsItsLink() throws Exception {
        Patient patient = accounts.adult();
        String address = address();
        String accountId = accounts.open(patient, address).get("id").asText();
        String token = lastTokenTo(address);

        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/deactivate").with(RECEPTION))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/patient-accounts/" + accountId + "/reactivate").with(RECEPTION))
                .andExpect(jsonPath("$.status").value("PENDING"));

        activation(token, PASSWORD, 400);
    }

    /** It logs in with their document, so nothing may leave them unverified (D-79). */
    @Test
    void aPatientWithAnAccountChangesTheirNameOnlyWithTheirDocumentSeen() throws Exception {
        Patient patient = accounts.withAccount();
        String renamed = """
                {"fullName": "Ana María Pérez", "documentType": "DNI", "documentNumber": "%s", "documentSeen": %s}
                """;

        mockMvc.perform(put("/api/patients/" + patient.id()).with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content(renamed.formatted(patient.dni(), false)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/patients/" + patient.id()).with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content(renamed.formatted(patient.dni(), true)))
                .andExpect(status().isOk());

        Patient without = accounts.adult();
        mockMvc.perform(put("/api/patients/" + without.id()).with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content(renamed.formatted(without.dni(), false)))
                .andExpect(status().isOk());
    }

    /** A past appointment the patient missed, and one cancelled before cancellations were recorded (D-76). */
    @Test
    void aPatientReadsTheirLastYearWithWhatBecameOfEachAppointment() throws Exception {
        Patient patient = accounts.withAccount();
        String absent = book(patient, "2026-03-12T10:00:00");
        String unrecorded = book(patient, "2026-03-12T11:00:00");
        jdbc.update("update appointment set status = 'ABSENT', starts_at = cast('2026-03-02 10:00:00' as timestamp) "
                + "where id = ?", UUID.fromString(absent));
        jdbc.update("update appointment set status = 'CANCELLED', starts_at = cast('2026-03-03 10:00:00' as timestamp) "
                + "where id = ?", UUID.fromString(unrecorded));

        mockMvc.perform(get("/api/portal/appointments").with(as(patient)))
                .andExpect(jsonPath("$.upcoming.length()").value(0))
                .andExpect(jsonPath("$.past.length()").value(2))
                .andExpect(jsonPath("$.past[0].id").value(unrecorded))
                .andExpect(jsonPath("$.past[0].state").value("CANCELLED"))
                .andExpect(jsonPath("$.past[0].cancellationRequestedBy").isEmpty())
                .andExpect(jsonPath("$.past[1].id").value(absent))
                .andExpect(jsonPath("$.past[1].state").value("ABSENT"));
    }

    @Test
    void theDatabaseKeepsAnAccountsPasswordAndHistoryInStep() throws Exception {
        UUID account = UUID.fromString(accounts.accountOf(accounts.withAccount()));
        String insertAccount = "insert into patient_account (id, patient_id, email, password_hash, status, opened_at) "
                + "values (?, ?, 'ana@mail.com', ?, ?, cast('2026-03-10 00:00:00' as timestamp))";
        String insertChange = "insert into patient_account_change (id, account_id, action, changed_at, changed_by) "
                + "values (?, ?, ?, cast('2026-03-10 00:00:00' as timestamp), ?)";

        assertThatThrownBy(() -> jdbc.update(insertAccount, UUID.randomUUID(), UUID.randomUUID(), "$2a$10$hash",
                "PENDING")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertAccount, UUID.randomUUID(), UUID.randomUUID(), null, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insertAccount, UUID.randomUUID(), UUID.randomUUID(), null, "INACTIVE");
        assertThatThrownBy(() -> jdbc.update(insertChange, UUID.randomUUID(), account, "ACTIVATED", "recepcion"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertChange, UUID.randomUUID(), account, "DEACTIVATED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insertChange, UUID.randomUUID(), account, "DEACTIVATED", "recepcion");
    }

    @Test
    void patientsAndStaffReachNothingOfEachOthers() throws Exception {
        Patient patient = accounts.withAccount();

        mockMvc.perform(get("/api/patients").with(as(patient))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/appointments").with(as(patient))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/appointments").with(RECEPTION)).andExpect(status().isForbidden());
        // Not recepcion, which the mock user already is: a login under the same name would not be tried again.
        mockMvc.perform(get("/api/portal/appointments").with(httpBasic("admin", "admin123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/patient-accounts").param("patientId", patient.id())
                        .with(user("arias").roles("PRACTITIONER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patient-accounts").param("patientId", patient.id())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/portal/appointments").with(httpBasic(patient.id(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/appointments").with(httpBasic("DNI " + patient.dni(), "otra-clave-larga")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/appointments")
                        .with(httpBasic("DNI " + patient.dni().substring(0, 2) + "." + patient.dni().substring(2),
                                PASSWORD)))
                .andExpect(status().isOk());
    }
}
