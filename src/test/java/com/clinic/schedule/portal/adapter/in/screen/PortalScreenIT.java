package com.clinic.schedule.portal.adapter.in.screen;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static com.clinic.schedule.portal.PatientAccountFixture.address;
import static com.clinic.schedule.portal.PatientAccountFixture.lastTokenTo;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The screens of the web accounts (D-79, D-80): reception's, on a patient's account, and the patients' own —
 * logging in with their document, activating the account and reading their appointments — on a login and a
 * session of their own.
 *
 * <p>No mock user for the whole class: it would stand in for the sessions the patients log in to. Reception
 * is named on each of its requests instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class PortalScreenIT {

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

    private MockHttpSession logIn(Patient patient, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/portal/login").with(csrf())
                        .param("documentType", "DNI").param("documentNumber", patient.dni())
                        .param("password", password))
                .andExpect(redirectedUrl("/portal/appointments"))
                .andReturn().getRequest().getSession(false);
    }

    /** The practitioners' fixture sends its requests as whoever the test is: reception, for the while. */
    private CreatedPractitioner practitioner() throws Exception {
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        try {
            return new PractitionerFixture(mockMvc, json).workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
    }

    private String book(CreatedPractitioner practitioner, Patient patient, String startsAt) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/appointments").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s",
                                 "note": "Nota de recepcion"}
                                """.formatted(patient.id(), practitioner.id(), practitioner.consulta(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String accountScreen(Patient patient) {
        return "/patient-accounts?patient=" + patient.id();
    }

    @Test
    void receptionOpensAnAccountFromThePatientsScreen() throws Exception {
        Patient patient = accounts.adult();
        String address = address();
        mockMvc.perform(get("/patients/" + patient.id()).with(RECEPTION))
                .andExpect(content().string(containsString("/patient-accounts?patient=" + patient.id())));
        mockMvc.perform(get(accountScreen(patient)).with(RECEPTION))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No tiene cuenta web")));

        mockMvc.perform(post("/patient-accounts").with(RECEPTION).with(csrf())
                        .param("patientId", patient.id()).param("email", address))
                .andExpect(redirectedUrl(accountScreen(patient)))
                .andExpect(flash().attribute("message", "Cuenta abierta: le enviamos el link de activación a "
                        + address + "."));

        mockMvc.perform(get(accountScreen(patient)).with(RECEPTION))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Pendiente de activación"), containsString(address),
                        containsString("Reenviar link de activación"), containsString("Link de activación enviado"),
                        containsString(">Abierta<"), containsString("recepcion"))));
    }

    @Test
    void receptionIsToldWhyAnAccountIsNotOpened() throws Exception {
        Patient adult = accounts.adult();
        accounts.open(adult, address());
        String[][] refusals = {
                {adult.id(), address(), "El paciente ya tiene cuenta web."},
                {accounts.patient(false, "1990-05-14").id(), address(), "primero verificá su identidad"},
                {accounts.patient(true, null).id(), address(), "cargá primero su fecha de nacimiento"},
                {accounts.patient(true, "2013-03-11").id(), address(), "13 años o más"},
                {accounts.adult().id(), "ana", "nombre@dominio.com"},
                {accounts.adult().id(), "falla-" + address(), "el email de activación no salió"}};
        for (String[] refusal : refusals) {
            mockMvc.perform(post("/patient-accounts").with(RECEPTION).with(csrf())
                            .param("patientId", refusal[0]).param("email", refusal[1]))
                    .andExpect(flash().attribute("error", containsString(refusal[2])));
        }
    }

    @Test
    void receptionSendsTheLinkAgainChangesTheEmailAndSwitchesTheAccountOffAndOn() throws Exception {
        Patient patient = accounts.adult();
        String accountId = accounts.open(patient, address()).get("id").asText();
        String other = address();

        mockMvc.perform(post("/patient-accounts/" + accountId + "/activation-resend").with(RECEPTION).with(csrf()))
                .andExpect(redirectedUrl(accountScreen(patient)))
                .andExpect(flash().attribute("message", containsString("Le reenviamos el link")));
        mockMvc.perform(post("/patient-accounts/" + accountId + "/email-change").with(RECEPTION).with(csrf())
                        .param("email", other))
                .andExpect(flash().attribute("message", "Email cambiado: le enviamos el link de activación a "
                        + other + "."));
        mockMvc.perform(post("/patient-accounts/" + accountId + "/deactivate").with(RECEPTION).with(csrf()))
                .andExpect(flash().attribute("message", containsString("Cuenta desactivada")));
        mockMvc.perform(post("/patient-accounts/" + accountId + "/deactivate").with(RECEPTION).with(csrf()))
                .andExpect(flash().attribute("error", containsString("ya no está en ese estado")));
        mockMvc.perform(get(accountScreen(patient)).with(RECEPTION))
                .andExpect(content().string(allOf(containsString("Desactivada"), containsString("Reactivar"))));
        mockMvc.perform(post("/patient-accounts/" + accountId + "/reactivate").with(RECEPTION).with(csrf()))
                .andExpect(flash().attribute("message", containsString("reenviale el link de activación")));
        mockMvc.perform(get(accountScreen(patient)).with(RECEPTION))
                .andExpect(content().string(allOf(containsString(">Email cambiado<"), containsString(">Desactivada<"),
                        containsString(">Reactivada<"), containsString("<strong>Pendiente de activación</strong>"))));
        mockMvc.perform(post("/patient-accounts/" + accountId + "/email-change").with(RECEPTION).with(csrf())
                        .param("email", "falla-" + address()))
                .andExpect(flash().attribute("error",
                        "Email cambiado, pero el link de activación no salió: reenvialo."));
        mockMvc.perform(post("/patient-accounts/" + accountId + "/activation-resend").with(RECEPTION).with(csrf()))
                .andExpect(flash().attribute("error", containsString("probá de nuevo más tarde")));

        Patient active = accounts.withAccount();
        String activeId = accounts.accountOf(active);
        mockMvc.perform(post("/patient-accounts/" + activeId + "/email-change").with(RECEPTION).with(csrf())
                        .param("email", other))
                .andExpect(flash().attribute("message", "Email de la cuenta: " + other + "."));
        mockMvc.perform(post("/patient-accounts/" + activeId + "/deactivate").with(RECEPTION).with(csrf()));
        mockMvc.perform(post("/patient-accounts/" + activeId + "/reactivate").with(RECEPTION).with(csrf()))
                .andExpect(flash().attribute("message", containsString("puede ingresar con su contraseña")));
        mockMvc.perform(get(accountScreen(active)).with(RECEPTION))
                .andExpect(content().string(allOf(containsString("<strong>Activa</strong>"),
                        containsString(">Activada<"), containsString(">el paciente<"))));
    }

    @Test
    void thePatientActivatesTheirAccountOnTheScreen() throws Exception {
        Patient patient = accounts.adult();
        String address = address();
        accounts.open(patient, address);
        String token = lastTokenTo(address);

        mockMvc.perform(get("/portal/activation").param("token", "otro"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Este link ya no sirve")));
        mockMvc.perform(get("/portal/activation").param("token", token))
                .andExpect(content().string(allOf(containsString("Elegí tu contraseña"),
                        not(containsString("Este link ya no sirve")))));
        mockMvc.perform(post("/portal/activation").with(csrf()).param("token", token)
                        .param("password", PASSWORD).param("repeatedPassword", "otra-clave-larga"))
                .andExpect(content().string(containsString("no coinciden")));
        mockMvc.perform(post("/portal/activation").with(csrf()).param("token", token)
                        .param("password", "corta").param("repeatedPassword", "corta"))
                .andExpect(content().string(containsString("de 10 a 72 caracteres")));
        mockMvc.perform(post("/portal/activation").with(csrf()).param("token", token)
                        .param("password", PASSWORD).param("repeatedPassword", PASSWORD))
                .andExpect(redirectedUrl("/portal/login?activated"));
        mockMvc.perform(post("/portal/activation").with(csrf()).param("token", token)
                        .param("password", PASSWORD).param("repeatedPassword", PASSWORD))
                .andExpect(content().string(containsString("Este link ya no sirve")));

        logIn(patient, PASSWORD);
    }

    @Test
    void aPatientLogsInWithTheirDocumentAndReadsTheirAppointments() throws Exception {
        CreatedPractitioner arias = practitioner();
        Patient patient = accounts.withAccount();
        book(arias, patient, "2026-03-12T10:00:00");
        String[][] cancellations = {{"2026-03-13T10:00:00", "PRACTICE"}, {"2026-03-14T10:00:00", "PATIENT"}};
        for (String[] cancelled : cancellations) {
            String appointment = book(arias, patient, cancelled[0]);
            mockMvc.perform(post("/api/appointments/" + appointment + "/cancel").with(RECEPTION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"requestedBy\": \"" + cancelled[1] + "\"}"))
                    .andExpect(status().isOk());
        }
        String absent = book(arias, patient, "2026-03-15T10:00:00");
        jdbc.update("update appointment set status = 'ABSENT', starts_at = cast('2026-03-02 10:00:00' as timestamp) "
                + "where id = ?", UUID.fromString(absent));
        String unrecorded = book(arias, patient, "2026-03-16T10:00:00");
        jdbc.update("update appointment set status = 'CANCELLED' where id = ?", UUID.fromString(unrecorded));

        mockMvc.perform(get("/portal/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("PASSPORT"), containsString("documentNumber"))));
        mockMvc.perform(post("/portal/login").with(csrf()).param("documentType", "DNI")
                        .param("documentNumber", patient.dni()).param("password", "otra-clave-larga"))
                .andExpect(redirectedUrl("/portal/login?error"));
        MockHttpSession session = logIn(patient, PASSWORD);

        mockMvc.perform(get("/portal").session(session)).andExpect(redirectedUrl("/portal/appointments"));
        mockMvc.perform(get("/portal/appointments").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Mis turnos"),
                        containsString("jueves 12/03/2026 10:00"),
                        containsString("Dra. Arias"), containsString("consulta"), containsString("Reservado"),
                        containsString("Cancelado por el consultorio"), containsString("Cancelado a tu pedido"),
                        containsString(">Cancelado<"), containsString("No asististe"),
                        containsString("lunes 2/03/2026 10:00"), not(containsString("No tuviste turnos")),
                        not(containsString("Nota de recepcion")), not(containsString(">Agenda<")))));
    }

    @Test
    void theStaffAndThePatientsAreNobodyOnEachOthersScreens() throws Exception {
        mockMvc.perform(get("/portal/appointments")).andExpect(redirectedUrl("http://localhost/portal/login"));

        MockHttpSession patient = logIn(accounts.withAccount(), PASSWORD);
        mockMvc.perform(get("/agenda").session(patient)).andExpect(redirectedUrl("http://localhost/login"));

        MockHttpSession staff = (MockHttpSession) mockMvc.perform(formLogin("/login").user("recepcion")
                        .password("recepcion123"))
                .andReturn().getRequest().getSession(false);
        mockMvc.perform(get("/portal/appointments").session(staff))
                .andExpect(redirectedUrl("http://localhost/portal/login"));
    }

    /** Not when the session runs out: at the very next request (D-79). */
    @Test
    void aDeactivatedAccountIsSentOutOfTheSessionItHadOpen() throws Exception {
        Patient patient = accounts.withAccount();
        MockHttpSession session = logIn(patient, PASSWORD);
        mockMvc.perform(get("/portal/appointments").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/patient-accounts/" + accounts.accountOf(patient) + "/deactivate").with(RECEPTION));

        mockMvc.perform(get("/portal/appointments").session(session))
                .andExpect(redirectedUrl("/portal/login?expired"));
    }

    @Test
    void receptionIsToldWhyTheNameOfAPatientWithAnAccountDoesNotChange() throws Exception {
        Patient patient = accounts.withAccount();

        mockMvc.perform(post("/patients/" + patient.id()).with(RECEPTION).with(csrf())
                        .param("fullName", "Ana María Pérez").param("documentType", "DNI")
                        .param("documentNumber", patient.dni()))
                .andExpect(flash().attribute("error", containsString("tiene cuenta web")));
    }
}
