package com.clinic.schedule.portal.adapter.in.screen;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The patients' password on their screens (D-86), in Spanish: the login that says the same of every failure,
 * locking after five; asking for the link and choosing a new password from it; changing it logged in; and
 * reception reading all of it in the account's history.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class PasswordScreenIT {

    private static final String NEW_PASSWORD = "otra-clave-larga";
    private static final String FAILED = "No pudimos ingresarte: revisá el documento y la contraseña.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private PatientAccountFixture accounts;

    @BeforeEach
    void setUp() {
        accounts = new PatientAccountFixture(mockMvc, json);
    }

    private ResultActions logIn(Patient patient, String password) throws Exception {
        return mockMvc.perform(post("/portal/login").with(csrf()).param("documentType", "DNI")
                .param("documentNumber", patient.dni()).param("password", password));
    }

    private String accountScreen(Patient patient) {
        return "/patient-accounts?patient=" + patient.id();
    }

    @Test
    void theLoginSaysTheSameOfEveryFailureAndLocksAfterFive() throws Exception {
        Patient patient = accounts.withAccount();
        mockMvc.perform(get("/portal/login"))
                .andExpect(content().string(allOf(containsString("href=\"/portal/password-recovery\""),
                        containsString(">¿Olvidaste tu contraseña?<"), not(containsString(FAILED)))));
        mockMvc.perform(get("/portal/login").param("error", ""))
                .andExpect(content().string(allOf(containsString(FAILED),
                        containsString("5 intentos fallidos la cuenta se bloquea 15 minutos"))));

        for (int i = 0; i < 5; i++) {
            logIn(patient, "no-es-la-clave").andExpect(redirectedUrl("/portal/login?error"));
        }
        logIn(patient, PASSWORD).andExpect(redirectedUrl("/portal/login?error"));
        String history = mockMvc.perform(get(accountScreen(patient)).with(RECEPTION))
                .andReturn().getResponse().getContentAsString();
        // Locked by nobody: the failed logins say it.
        assertThat(history).containsPattern(">Bloqueada por intentos fallidos</td>\\s*<td></td>");
        mockMvc.perform(get("/portal/login").param("expired", ""))
                .andExpect(content().string(containsString("tu cuenta fue desactivada o cambió su")));
    }

    @Test
    void aPatientAsksForTheLinkAndChoosesANewPasswordFromIt() throws Exception {
        Patient patient = accounts.withAccount();
        MockHttpSession before = (MockHttpSession) logIn(patient, PASSWORD)
                .andExpect(redirectedUrl("/portal/appointments")).andReturn().getRequest().getSession(false);
        mockMvc.perform(get("/portal/password-recovery"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("PASSPORT"), containsString("Mandame el link"))));
        mockMvc.perform(post("/portal/password-recovery").with(csrf()).param("documentType", "DNI")
                        .param("documentNumber", patient.dni()))
                .andExpect(redirectedUrl("/portal/password-recovery"))
                .andExpect(flash().attribute("message", "Si ese documento tiene una cuenta, le mandamos un link al "
                        + "email de la cuenta para elegir una contraseña nueva. Vale una hora."));
        mockMvc.perform(post("/portal/password-recovery").with(csrf()).param("documentType", "DNI")
                        .param("documentNumber", "1"))
                .andExpect(flash().attribute("message", startsWith("Si ese documento")));
        String token = KeptAccountMail.RESETS.get(KeptAccountMail.RESETS.size() - 1).token();

        // The email's link opens the patients' application, which chooses it through the API (D-110).
        mockMvc.perform(get("/portal/password-reset").param("token", token))
                .andExpect(redirectedUrl("/portal/app/password-reset?token=" + token));
        mockMvc.perform(post("/api/portal/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        logIn(patient, NEW_PASSWORD).andExpect(redirectedUrl("/portal/appointments"));
        // Whoever was logged in with the old password is sent out.
        mockMvc.perform(get("/portal/appointments").session(before))
                .andExpect(redirectedUrl("/portal/login?expired"));

        mockMvc.perform(get(accountScreen(patient)).with(RECEPTION))
                .andExpect(content().string(allOf(containsString(">Link para elegir contraseña enviado<"),
                        containsString(">pedido desde la web<"), containsString(">Contraseña nueva desde el link<"),
                        containsString(">el paciente<"))));
    }

    @Test
    void thePatientChangesTheirPasswordOnTheirScreen() throws Exception {
        Patient patient = accounts.withAccount();
        MockHttpSession session = (MockHttpSession) logIn(patient, PASSWORD)
                .andExpect(redirectedUrl("/portal/appointments")).andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/portal/password").session(session))
                .andExpect(content().string(allOf(containsString("Cambiar tu contraseña"),
                        containsString("href=\"/portal/password\""))));
        String[][] refusals = {
                {"no-es-la-clave", NEW_PASSWORD, NEW_PASSWORD, "La contraseña actual no es esa."},
                {PASSWORD, PASSWORD, PASSWORD, "La contraseña nueva es igual a la actual: elegí otra."},
                {PASSWORD, "corta", "corta", "La contraseña tiene de 10 a 72 caracteres."},
                {PASSWORD, NEW_PASSWORD, "otra-distinta-larga", "Las dos contraseñas nuevas no coinciden."}};
        for (String[] refusal : refusals) {
            mockMvc.perform(post("/portal/password").session(session).with(csrf())
                            .param("currentPassword", refusal[0]).param("newPassword", refusal[1])
                            .param("repeatedPassword", refusal[2]))
                    .andExpect(redirectedUrl("/portal/password"))
                    .andExpect(flash().attribute("error", refusal[3]));
        }
        mockMvc.perform(post("/portal/password").session(session).with(csrf()).param("currentPassword", PASSWORD)
                        .param("newPassword", NEW_PASSWORD).param("repeatedPassword", NEW_PASSWORD))
                .andExpect(flash().attribute("message", "Tu contraseña quedó cambiada."));
        // The session goes on.
        mockMvc.perform(get("/portal/booking").session(session)).andExpect(status().isOk());
        logIn(patient, NEW_PASSWORD).andExpect(redirectedUrl("/portal/appointments"));
        mockMvc.perform(get(accountScreen(patient)).with(RECEPTION))
                .andExpect(content().string(containsString(">Contraseña cambiada<")));
        mockMvc.perform(get("/portal/password")).andExpect(redirectedUrl("http://localhost/portal/login"));
    }
}
