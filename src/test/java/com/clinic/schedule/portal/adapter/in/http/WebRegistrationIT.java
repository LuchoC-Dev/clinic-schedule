package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static com.clinic.schedule.portal.PatientAccountFixture.dni;
import static com.clinic.schedule.portal.PatientAccountFixture.lastRegistrationTokenTo;
import static com.clinic.schedule.portal.PatientAccountFixture.registration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.clinic.schedule.portal.application.usecase.RegisterOnWebUseCase;
import com.clinic.schedule.portal.domain.RegistrationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An adult registers themselves on the web over the real stack (D-91): confirmed from the link in their email,
 * they are a patient, unverified and marked as registered on the web, with an account in use; a document the
 * practice knows opens nothing and gets the same answer; a registration never confirmed is forgotten. "Today" is 10
 * March 2026, at midnight, and the tests' clock stands still: a registration is moved in the database to expire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class WebRegistrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegisterOnWebUseCase registrations;

    private PatientAccountFixture accounts;

    @BeforeEach
    void setUp() {
        accounts = new PatientAccountFixture(mockMvc, json);
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/portal/registrations").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/portal/registrations/confirmation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }

    private ResultActions logIn(String dni) throws Exception {
        return mockMvc.perform(get("/api/portal/appointments").with(httpBasic("DNI " + dni, PASSWORD)));
    }

    /** The patients of that document, as reception's search finds them. */
    private JsonNode patientsOf(String dni) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/patients").with(RECEPTION).param("query", dni))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private int waiting(String email) {
        return jdbc.queryForObject("select count(*) from web_registration where email = ?", Integer.class, email);
    }

    private long refusedTo(String email) {
        return KeptAccountMail.REGISTRATIONS_REFUSED.stream().filter(sent -> sent.address().equals(email)).count();
    }

    private long linksTo(String email) {
        return KeptAccountMail.REGISTRATIONS.stream().filter(sent -> sent.address().equals(email)).count();
    }

    @Test
    void anAdultRegistersConfirmsFromTheEmailAndIsAnUnverifiedPatientRegisteredOnTheWeb() throws Exception {
        String dni = dni();
        String email = address();
        register("""
                {"fullName": " Ana Pérez ", "documentType": "DNI", "documentNumber": "%s", "birthDate": "1990-05-14",
                 "phone": "11 5555-0000", "email": "%s", "password": "%s"}
                """.formatted(dni, email.toUpperCase(), PASSWORD))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        RegistrationMessage sent = KeptAccountMail.REGISTRATIONS.stream()
                .filter(message -> message.address().equals(email)).findFirst().orElseThrow();
        assertThat(sent.fullName()).isEqualTo("Ana Pérez");
        assertThat(sent.expiresAt()).isEqualTo(LocalDateTime.of(2026, 3, 13, 0, 0));
        // Until it is confirmed nobody is a patient, nor has an account; the password is kept hashed alone.
        assertThat(patientsOf(dni)).isEmpty();
        logIn(dni).andExpect(status().isUnauthorized());
        assertThat(waiting(email)).isOne();
        assertThat(jdbc.queryForObject("select password_hash from web_registration where email = ?", String.class,
                email)).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject("select token_hash from web_registration where email = ?", String.class,
                email)).hasSize(64).isNotEqualTo(sent.token());

        // The patients' application asks whether the link confirms, with nobody logged in, before its button (D-110).
        mockMvc.perform(get("/api/portal/registrations/confirmation/" + sent.token())).andExpect(status().isNoContent());
        assertThat(waiting(email)).isOne();
        confirm(sent.token()).andExpect(status().isOk()).andExpect(jsonPath("$.notRegistered").isEmpty());
        mockMvc.perform(get("/api/portal/registrations/confirmation/" + sent.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_LINK"));

        logIn(dni).andExpect(status().isOk());
        JsonNode patient = patientsOf(dni).get(0);
        assertThat(patient.get("fullName").asText()).isEqualTo("Ana Pérez");
        assertThat(patient.get("identity").asText()).isEqualTo("DECLARED");
        assertThat(patient.get("registeredVia").asText()).isEqualTo("WEB");
        assertThat(patient.get("birthDate").asText()).isEqualTo("1990-05-14");
        assertThat(patient.get("phone").get("number").asText()).isEqualTo("1155550000");
        assertThat(patient.get("phone").get("owner").isNull()).isTrue();
        // The account's email is its own, not the patient's contact (D-79).
        assertThat(patient.get("email").isNull()).isTrue();
        String patientId = patient.get("id").asText();
        mockMvc.perform(get("/api/patient-accounts").with(RECEPTION).param("patientId", patientId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value(email));
        String account = accounts.accountOf(new Patient(patientId, dni));
        mockMvc.perform(get("/api/patient-accounts/" + account + "/changes").with(RECEPTION))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("REGISTERED"))
                .andExpect(jsonPath("$[0].by").isEmpty());

        // The registration is forgotten, and its link confirms nothing more.
        assertThat(waiting(email)).isZero();
        confirm(sent.token()).andExpect(status().isBadRequest());
    }

    @Test
    void aPatientRegisteredAtReceptionSaysSo() throws Exception {
        Patient patient = accounts.adult();
        mockMvc.perform(get("/api/patients/" + patient.id()).with(RECEPTION))
                .andExpect(jsonPath("$.registeredVia").value("RECEPTION"));
    }

    /** Nobody learns from the web whether a document is a patient's: the answer is the same, only the email tells. */
    @Test
    void aDocumentThePracticeKnowsOpensNothingAndIsAnsweredTheSame() throws Exception {
        Patient known = accounts.adult();
        String email = address();
        MvcResult toKnown = register(registration(known.dni(), email)).andReturn();
        MvcResult toUnknown = register(registration(dni(), address())).andReturn();

        assertThat(toKnown.getResponse().getStatus()).isEqualTo(202).isEqualTo(toUnknown.getResponse().getStatus());
        assertThat(toKnown.getResponse().getContentAsString()).isEqualTo(toUnknown.getResponse().getContentAsString());
        assertThat(toKnown.getResponse().getHeaderNames())
                .containsExactlyInAnyOrderElementsOf(toUnknown.getResponse().getHeaderNames());
        assertThat(refusedTo(email)).isOne();
        assertThat(linksTo(email)).isZero();
        assertThat(waiting(email)).isZero();
        assertThat(patientsOf(known.dni())).hasSize(1);
        mockMvc.perform(get("/api/patient-accounts").with(RECEPTION).param("patientId", known.id()))
                .andExpect(status().isNotFound());
        // Written another way, the document is still the one the practice knows.
        String other = address();
        register(registration(known.dni().substring(0, 2) + "." + known.dni().substring(2), other))
                .andExpect(status().isAccepted());
        assertThat(refusedTo(other)).isOne();
        assertThat(waiting(other)).isZero();
    }

    /** Whoever confirms first is registered; the document is someone's by the time the other confirms. */
    @Test
    void aDocumentRegisteredMeanwhileRegistersNothingAndTheRegistrationIsForgotten() throws Exception {
        String dni = dni();
        String first = address();
        String second = address();
        register(registration(dni, first)).andExpect(status().isAccepted());
        register(registration(dni, second)).andExpect(status().isAccepted());
        assertThat(waiting(first)).isOne();
        assertThat(waiting(second)).isOne();

        confirm(lastRegistrationTokenTo(second)).andExpect(status().isOk());
        confirm(lastRegistrationTokenTo(first)).andExpect(status().isConflict());

        assertThat(waiting(first)).isZero();
        assertThat(patientsOf(dni)).hasSize(1);
        mockMvc.perform(get("/api/patient-accounts").with(RECEPTION)
                        .param("patientId", patientsOf(dni).get(0).get("id").asText()))
                .andExpect(jsonPath("$.email").value(second));
        confirm(lastRegistrationTokenTo(first)).andExpect(status().isBadRequest());
    }

    /** What the form alone can tell is said, and nothing is kept. */
    @Test
    void aRegistrationThatCannotBeAPatientsOrAnAdultsIsRefusedAndKeepsNothing() throws Exception {
        String email = address();
        String base = """
                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%s", "birthDate": %s, "phone": "%s",
                 "email": "%s", "password": "%s"}""";
        // 18 on 10 March 2026 is born by 10 March 2008.
        register(base.formatted("Ana Pérez", dni(), "\"2008-03-11\"", "1155550000", email, PASSWORD))
                .andExpect(status().isConflict());
        register(base.formatted("Ana Pérez", dni(), "\"2008-03-10\"", "1155550000", address(), PASSWORD))
                .andExpect(status().isAccepted());
        register(base.formatted("Ana Pérez", dni(), "null", "1155550000", email, PASSWORD))
                .andExpect(status().isBadRequest());
        register(base.formatted("Ana Pérez", "12AB", "\"1990-05-14\"", "1155550000", email, PASSWORD))
                .andExpect(status().isBadRequest());
        register(base.formatted("Ana Pérez", "", "\"1990-05-14\"", "1155550000", email, PASSWORD))
                .andExpect(status().isBadRequest());
        register(base.formatted("123", dni(), "\"1990-05-14\"", "1155550000", email, PASSWORD))
                .andExpect(status().isBadRequest());
        register(base.formatted("Ana Pérez", dni(), "\"1990-05-14\"", "12", email, PASSWORD))
                .andExpect(status().isBadRequest());
        register(base.formatted("Ana Pérez", dni(), "\"1990-05-14\"", "1155550000", "ana-at-mail", PASSWORD))
                .andExpect(status().isBadRequest());
        register(base.formatted("Ana Pérez", dni(), "\"1990-05-14\"", "1155550000", email, "corta"))
                .andExpect(status().isBadRequest());

        assertThat(waiting(email)).isZero();
        assertThat(linksTo(email) + refusedTo(email)).isZero();
    }

    /** Under 18 is refused for a known document as for any other, before the document is looked at. */
    @Test
    void aMinorIsRefusedTheSameWhetherTheDocumentIsKnownOrNot() throws Exception {
        Patient known = accounts.adult();
        String body = """
                {"fullName": "Ana Pérez", "documentType": "DNI", "documentNumber": "%s", "birthDate": "2010-01-01",
                 "email": "%s", "password": "%s"}""";
        String email = address();
        register(body.formatted(known.dni(), email, PASSWORD)).andExpect(status().isConflict());
        register(body.formatted(dni(), address(), PASSWORD)).andExpect(status().isConflict());
        assertThat(refusedTo(email)).isZero();
    }

    @Test
    void anEmailThatDoesNotGoOutKeepsNothingWhicheverItWas() throws Exception {
        String email = "falla-" + address();
        register(registration(dni(), email)).andExpect(status().isServiceUnavailable());
        register(registration(accounts.adult().dni(), email)).andExpect(status().isServiceUnavailable());
        assertThat(waiting(email)).isZero();
    }

    /** A link finds one registration at most: the hash of its token is unique (V43). */
    @Test
    void twoRegistrationsCannotShareALink() throws Exception {
        String email = address();
        register(registration(dni(), email)).andExpect(status().isAccepted());
        assertThatThrownBy(() -> jdbc.update("""
                insert into web_registration (id, token_hash, full_name, document_type, document_number, birth_date,
                    email, password_hash, requested_at, expires_at)
                select ?, token_hash, full_name, document_type, document_number, birth_date, email, password_hash,
                    requested_at, expires_at
                from web_registration where email = ?""", UUID.randomUUID(), email))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRegistrationNeverConfirmedExpiresAndIsForgotten() throws Exception {
        String dni = dni();
        String expired = address();
        String current = address();
        register(registration(dni, expired)).andExpect(status().isAccepted());
        register(registration(dni(), current)).andExpect(status().isAccepted());
        jdbc.update("update web_registration set expires_at = ? where email = ?",
                LocalDateTime.of(2026, 3, 10, 0, 0), expired);

        confirm(lastRegistrationTokenTo(expired)).andExpect(status().isBadRequest());
        assertThat(patientsOf(dni)).isEmpty();
        confirm("no-es-un-link").andExpect(status().isBadRequest());

        assertThat(registrations.forgetExpired()).isPositive();
        assertThat(waiting(expired)).isZero();
        assertThat(waiting(current)).isOne();
    }
}
