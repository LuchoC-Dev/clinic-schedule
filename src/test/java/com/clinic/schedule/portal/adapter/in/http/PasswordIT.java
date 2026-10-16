package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.clinic.schedule.portal.domain.PasswordResetMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The patients' passwords over the real stack (D-86): five failed logins in a row lock an account for 15
 * minutes, a link to the account's email lets the patient choose a new one, and they change it themselves.
 * "Today" is 10 March 2026, at midnight, and the tests' clock stands still: a lock, or a link, is moved in the
 * database to have it run out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class PasswordIT {

    private static final String NEW_PASSWORD = "otra-clave-larga";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private PatientAccountFixture accounts;

    @BeforeEach
    void setUp() {
        accounts = new PatientAccountFixture(mockMvc, json);
    }

    private ResultActions logIn(Patient patient, String password) throws Exception {
        return mockMvc.perform(get("/api/portal/appointments").with(httpBasic("DNI " + patient.dni(), password)));
    }

    private ResultActions send(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions ask(Patient patient) throws Exception {
        return send("/api/portal/password-recovery",
                "{\"documentType\": \"DNI\", \"documentNumber\": \"" + patient.dni() + "\"}");
    }

    private List<PasswordResetMessage> resetsTo(String address) {
        return KeptAccountMail.RESETS.stream().filter(message -> message.address().equals(address)).toList();
    }

    private String emailOf(Patient patient) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/patient-accounts").with(RECEPTION)
                        .param("patientId", patient.id()))
                .andReturn().getResponse().getContentAsString()).get("email").asText();
    }

    private List<String> history(Patient patient) throws Exception {
        String account = accounts.accountOf(patient);
        JsonNode changes = json.readTree(mockMvc.perform(get("/api/patient-accounts/" + account + "/changes")
                .with(RECEPTION)).andReturn().getResponse().getContentAsString());
        return changes.findValuesAsText("action");
    }

    private LocalDateTime lockedUntil(Patient patient) {
        return jdbc.queryForObject("select locked_until from patient_account where patient_id = ?",
                LocalDateTime.class, UUID.fromString(patient.id()));
    }

    @Test
    void fiveFailedLoginsInARowLockTheAccountForFifteenMinutes() throws Exception {
        Patient patient = accounts.withAccount();
        for (int i = 0; i < 4; i++) {
            logIn(patient, "no-es-la-clave").andExpect(status().isUnauthorized());
        }
        // A login that goes through starts the count again.
        logIn(patient, PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            logIn(patient, "no-es-la-clave").andExpect(status().isUnauthorized());
        }
        logIn(patient, PASSWORD).andExpect(status().isOk());
        assertThat(history(patient)).doesNotContain("LOCKED");

        for (int i = 0; i < 5; i++) {
            logIn(patient, "no-es-la-clave").andExpect(status().isUnauthorized());
        }
        logIn(patient, PASSWORD).andExpect(status().isUnauthorized());
        assertThat(lockedUntil(patient)).isEqualTo(LocalDateTime.of(2026, 3, 10, 0, 15));
        // Trying while it is locked does not lengthen it.
        logIn(patient, "no-es-la-clave").andExpect(status().isUnauthorized());
        assertThat(lockedUntil(patient)).isEqualTo(LocalDateTime.of(2026, 3, 10, 0, 15));
        assertThat(history(patient)).containsOnlyOnce("LOCKED");

        jdbc.update("update patient_account set locked_until = ? where patient_id = ?",
                LocalDateTime.of(2026, 3, 9, 23, 59), UUID.fromString(patient.id()));
        logIn(patient, PASSWORD).andExpect(status().isOk());
        assertThat(lockedUntil(patient)).isNull();
    }

    @Test
    void neitherADocumentWithoutAnAccountNorTheStaffAreLocked() throws Exception {
        Patient noAccount = accounts.adult();
        for (int i = 0; i < 6; i++) {
            logIn(noAccount, "no-es-la-clave").andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/patients").with(httpBasic("recepcion", "no-es-la-clave")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/patients").with(httpBasic("recepcion", "recepcion123")).param("query", "x"))
                .andExpect(status().isOk());
    }

    @Test
    void aForgottenPasswordIsChosenAgainFromTheLinkInTheAccountsEmail() throws Exception {
        Patient patient = accounts.withAccount();
        String address = emailOf(patient);
        for (int i = 0; i < 5; i++) {
            logIn(patient, "no-es-la-clave").andExpect(status().isUnauthorized());
        }

        ask(patient).andExpect(status().isNoContent());
        List<PasswordResetMessage> sent = resetsTo(address);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).fullName()).isEqualTo("Ana Pérez");
        assertThat(sent.get(0).expiresAt()).isEqualTo(LocalDateTime.of(2026, 3, 10, 1, 0));
        // Asked again too soon: the same answer, and no other email.
        ask(patient).andExpect(status().isNoContent());
        assertThat(resetsTo(address)).hasSize(1);
        String token = sent.get(0).token();
        // Asked by the patients' application before its form, with nobody logged in (D-110).
        mockMvc.perform(get("/api/portal/password-reset/" + token)).andExpect(status().isNoContent());

        send("/api/portal/password-reset", "{\"token\": \"" + token + "\", \"password\": \"corta\"}")
                .andExpect(status().isBadRequest());
        send("/api/portal/password-reset", "{\"token\": \"" + token + "\", \"password\": \"" + NEW_PASSWORD + "\"}")
                .andExpect(status().isNoContent());
        logIn(patient, NEW_PASSWORD).andExpect(status().isOk());
        logIn(patient, PASSWORD).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/password-reset/" + token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_LINK"));
        send("/api/portal/password-reset", "{\"token\": \"" + token + "\", \"password\": \"una-tercera-clave\"}")
                .andExpect(status().isBadRequest());
        assertThat(history(patient)).contains("LOCKED", "RECOVERY_SENT", "PASSWORD_RESET");

        // Five minutes on, another goes out, and it replaces the last.
        jdbc.update("update patient_account_password_reset set issued_at = ? where account_id = ?",
                LocalDateTime.of(2026, 3, 9, 23, 55), UUID.fromString(accounts.accountOf(patient)));
        ask(patient).andExpect(status().isNoContent());
        assertThat(resetsTo(address)).hasSize(2);
    }

    @Test
    void aLinkThatRanOutOrWasReplacedResetsNothing() throws Exception {
        Patient patient = accounts.withAccount();
        String address = emailOf(patient);
        ask(patient);
        String first = resetsTo(address).get(0).token();
        String account = accounts.accountOf(patient);
        jdbc.update("update patient_account_password_reset set issued_at = ? where account_id = ?",
                LocalDateTime.of(2026, 3, 9, 23, 0), UUID.fromString(account));
        ask(patient);
        String second = resetsTo(address).get(1).token();

        mockMvc.perform(get("/api/portal/password-reset/" + first)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/portal/password-reset/" + second)).andExpect(status().isNoContent());
        send("/api/portal/password-reset", "{\"token\": \"" + first + "\", \"password\": \"" + NEW_PASSWORD + "\"}")
                .andExpect(status().isBadRequest());
        jdbc.update("update patient_account_password_reset set expires_at = ? where account_id = ?",
                LocalDateTime.of(2026, 3, 10, 0, 0), UUID.fromString(account));
        mockMvc.perform(get("/api/portal/password-reset/" + second)).andExpect(status().isBadRequest());
        send("/api/portal/password-reset", "{\"token\": \"" + second + "\", \"password\": \"" + NEW_PASSWORD + "\"}")
                .andExpect(status().isBadRequest());
        send("/api/portal/password-reset", "{\"password\": \"" + NEW_PASSWORD + "\"}")
                .andExpect(status().isBadRequest());
        logIn(patient, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void askingSaysTheSameForAnyDocumentAndSendsOnlyToAnAccountInUse() throws Exception {
        Patient pending = accounts.adult();
        String pendingAddress = PatientAccountFixture.address();
        accounts.open(pending, pendingAddress);
        Patient inactive = accounts.withAccount();
        String inactiveAddress = emailOf(inactive);
        mockMvc.perform(post("/api/patient-accounts/" + accounts.accountOf(inactive) + "/deactivate").with(RECEPTION))
                .andExpect(status().isOk());

        ask(pending).andExpect(status().isNoContent());
        ask(inactive).andExpect(status().isNoContent());
        send("/api/portal/password-recovery", "{\"documentType\": \"DNI\", \"documentNumber\": \"1\"}")
                .andExpect(status().isNoContent());
        send("/api/portal/password-recovery", "{}").andExpect(status().isNoContent());

        assertThat(resetsTo(pendingAddress)).isEmpty();
        assertThat(resetsTo(inactiveAddress)).isEmpty();
    }

    @Test
    void aLinkThatDidNotGoOutIsNeitherKeptNorRecorded() throws Exception {
        Patient patient = accounts.withAccount();
        String unreachable = "falla-" + PatientAccountFixture.address();
        mockMvc.perform(post("/api/patient-accounts/" + accounts.accountOf(patient) + "/email-change").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"" + unreachable + "\"}"))
                .andExpect(status().isOk());

        ask(patient).andExpect(status().isNoContent());

        assertThat(resetsTo(unreachable)).isEmpty();
        assertThat(history(patient)).doesNotContain("RECOVERY_SENT");
        assertThat(jdbc.queryForObject("select count(*) from patient_account_password_reset where account_id = ?",
                Integer.class, UUID.fromString(accounts.accountOf(patient)))).isZero();
    }

    @Test
    void anAccountDeactivatedAfterAskingResetsNothing() throws Exception {
        Patient patient = accounts.withAccount();
        String address = emailOf(patient);
        ask(patient);
        String token = resetsTo(address).get(0).token();
        mockMvc.perform(post("/api/patient-accounts/" + accounts.accountOf(patient) + "/deactivate").with(RECEPTION))
                .andExpect(status().isOk());

        send("/api/portal/password-reset", "{\"token\": \"" + token + "\", \"password\": \"" + NEW_PASSWORD + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void theDatabaseHoldsTheNewRulesOfTheHistoryAndTheCount() throws Exception {
        Patient patient = accounts.withAccount();
        UUID account = UUID.fromString(accounts.accountOf(patient));
        String insertChange = "insert into patient_account_change (id, account_id, action, changed_at, changed_by) "
                + "values (?, ?, ?, cast('2026-03-10 00:00:00' as timestamp), ?)";

        for (String action : List.of("RECOVERY_SENT", "PASSWORD_RESET", "PASSWORD_CHANGED", "LOCKED")) {
            assertThatThrownBy(() -> jdbc.update(insertChange, UUID.randomUUID(), account, action, "recepcion"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update(insertChange, UUID.randomUUID(), account, action, null);
        }
        assertThatThrownBy(() -> jdbc.update(insertChange, UUID.randomUUID(), account, "UNLOCKED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update patient_account set failed_logins = -1 where id = ?", account))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void thePatientChangesTheirOwnPasswordGivingTheCurrentOne() throws Exception {
        Patient patient = accounts.withAccount();
        String basic = "DNI " + patient.dni();

        mockMvc.perform(post("/api/portal/password").with(httpBasic(basic, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"no-es-la-clave\", \"newPassword\": \"" + NEW_PASSWORD
                                + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/password").with(httpBasic(basic, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/password").with(httpBasic(basic, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"corta\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/portal/password").with(httpBasic(basic, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \""
                                + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        logIn(patient, NEW_PASSWORD).andExpect(status().isOk());
        logIn(patient, PASSWORD).andExpect(status().isUnauthorized());
        assertThat(history(patient)).contains("PASSWORD_CHANGED");
        mockMvc.perform(post("/api/portal/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
