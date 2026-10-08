package com.clinic.schedule.portal.adapter.in.screen;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static com.clinic.schedule.portal.PatientAccountFixture.dni;
import static com.clinic.schedule.portal.PatientAccountFixture.lastRegistrationTokenTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Registering on the web on the patients' screens (D-91), in Spanish: the form, reached from the login; the page
 * that says an email went out, the same for a document the practice knows; the email's link, which confirms only
 * with its button; and reception reading on the patient's screen that they registered on the web.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class RegistrationScreenIT {

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

    private ResultActions register(String dni, String email, String birthDate, String password, String repeated)
            throws Exception {
        return mockMvc.perform(post("/portal/register").with(csrf()).param("fullName", "Ana Pérez")
                .param("documentType", "DNI").param("documentNumber", dni).param("birthDate", birthDate)
                .param("phone", "").param("email", email).param("password", password)
                .param("repeatedPassword", repeated));
    }

    private ResultActions register(String dni, String email) throws Exception {
        return register(dni, email, "1990-05-14", PASSWORD, PASSWORD);
    }

    private String patientIdOf(String dni) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/patients").with(RECEPTION).param("query", dni))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
    }

    @Test
    void anAdultRegistersFromTheLoginConfirmsWithTheButtonAndLogsIn() throws Exception {
        mockMvc.perform(get("/portal/login"))
                .andExpect(content().string(allOf(containsString("href=\"/portal/register\""),
                        containsString(">Registrate<"))));
        mockMvc.perform(get("/portal/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Para mayores de 18 años"),
                        containsString("name=\"birthDate\""), containsString("PASSPORT"))));

        String dni = dni();
        String email = address();
        register(dni, email)
                .andExpect(redirectedUrl("/portal/register/sent"))
                .andExpect(flash().attribute("email", email));
        mockMvc.perform(get("/portal/register/sent").flashAttr("email", email))
                .andExpect(content().string(containsString("Te mandamos un email a <strong>" + email + "</strong>.")));

        // Opening the link, as a mail reader may on its own, confirms nothing.
        String token = lastRegistrationTokenTo(email);
        mockMvc.perform(get("/portal/register/confirm").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString(">Confirmar registro<"),
                        containsString("value=\"" + token + "\""))));
        assertThat(jdbc.queryForObject("select count(*) from web_registration where email = ?", Integer.class,
                email)).isOne();

        mockMvc.perform(post("/portal/register/confirm").with(csrf()).param("token", token))
                .andExpect(redirectedUrl("/portal/login?registered"));
        mockMvc.perform(get("/portal/login").param("registered", ""))
                .andExpect(content().string(containsString("Registro confirmado")));
        mockMvc.perform(post("/portal/login").with(csrf()).param("documentType", "DNI").param("documentNumber", dni)
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/portal/appointments"));
        mockMvc.perform(get("/portal/register/confirm").param("token", token))
                .andExpect(content().string(allOf(containsString("Este link ya no sirve"),
                        not(containsString(">Confirmar registro<")))));

        String patientId = patientIdOf(dni);
        mockMvc.perform(get("/patients/" + patientId).with(RECEPTION))
                .andExpect(content().string(allOf(containsString("No verificada"),
                        containsString("Registrado desde la web"))));
        mockMvc.perform(get("/patient-accounts").with(RECEPTION).param("patient", patientId))
                .andExpect(content().string(containsString("Abierta al confirmar el registro desde la web")));
    }

    @Test
    void aPatientRegisteredAtReceptionIsNotSaidToBeFromTheWeb() throws Exception {
        Patient patient = accounts.adult();
        mockMvc.perform(get("/patients/" + patient.id()).with(RECEPTION))
                .andExpect(content().string(not(containsString("Registrado desde la web"))));
    }

    @Test
    void aKnownDocumentGetsTheSamePageAndItsLinkWouldSendThemToReception() throws Exception {
        Patient known = accounts.adult();
        register(known.dni(), address())
                .andExpect(redirectedUrl("/portal/register/sent"));

        String dni = dni();
        String first = address();
        String second = address();
        register(dni, first).andExpect(redirectedUrl("/portal/register/sent"));
        register(dni, second).andExpect(redirectedUrl("/portal/register/sent"));
        mockMvc.perform(post("/portal/register/confirm").with(csrf()).param("token", lastRegistrationTokenTo(second)))
                .andExpect(redirectedUrl("/portal/login?registered"));
        mockMvc.perform(post("/portal/register/confirm").with(csrf()).param("token", lastRegistrationTokenTo(first)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Acercate a recepción con tu documento"),
                        not(containsString("Este link ya no sirve")), not(containsString(">Confirmar registro<")))));
    }

    @Test
    void whatTheFormCanTellIsSaidAndWhatWasTypedComesBack() throws Exception {
        String email = address();
        register(dni(), email, "1990-05-14", PASSWORD, "otra-cosa-larga")
                .andExpect(content().string(allOf(containsString("Las dos contraseñas no coinciden."),
                        containsString("value=\"Ana Pérez\""), containsString("value=\"" + email + "\""),
                        containsString("value=\"1990-05-14\""), not(containsString(PASSWORD)))));
        register(dni(), email, "2010-01-01", PASSWORD, PASSWORD)
                .andExpect(content().string(containsString("Desde la web se registran mayores de 18 años.")));
        register("12AB", email)
                .andExpect(content().string(containsString("Revisá los datos")));
        register(dni(), "ana-at-mail")
                .andExpect(content().string(containsString("El email tiene la forma nombre@dominio.com")));
        register(dni(), email, "1990-05-14", "corta", "corta")
                .andExpect(content().string(containsString("La contraseña tiene de 10 a 72 caracteres.")));
        register(dni(), email, "", PASSWORD, PASSWORD)
                .andExpect(content().string(containsString("Falta tu fecha de nacimiento.")));
        register(dni(), "falla-" + email)
                .andExpect(content().string(containsString("No pudimos mandarte el email.")));
        assertThat(jdbc.queryForObject("select count(*) from web_registration where email in (?, ?)", Integer.class,
                email, "falla-" + email)).isZero();
    }
}
