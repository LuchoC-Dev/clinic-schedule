package com.clinic.schedule.portal.adapter.in.screen;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The family on the patients' screens (D-85), in Spanish: the members' appointments under *Mis turnos*, booking
 * for one of them, and *Mi familia*. "Today" is 10 March 2026, at midnight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class FamilyPortalScreenIT {

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
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        try {
            arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
        mockMvc.perform(put("/api/practitioners/" + arias.id() + "/reasons/" + arias.consulta()).with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"consulta\", \"durationMinutes\": 30, \"webBookable\": true}"))
                .andExpect(status().isOk());
    }

    private MockHttpSession logIn(Patient patient) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/portal/login").with(csrf())
                        .param("documentType", "DNI").param("documentNumber", patient.dni())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/portal/appointments"))
                .andReturn().getRequest().getSession(false);
    }

    private String group(String name) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/family-groups").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void join(String group, Patient patient, boolean shares) throws Exception {
        mockMvc.perform(post("/api/family-groups/" + group + "/members").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\": \"" + patient.id() + "\", \"shares\": " + shares + "}"))
                .andExpect(status().isOk());
    }

    private void addChild(String group, Patient child, Patient parent) throws Exception {
        mockMvc.perform(post("/api/family-groups/" + group + "/children").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "requestedBy": "%s", "relation": "FATHER", "proofSeen": true}
                                """.formatted(child.id(), parent.id())))
                .andExpect(status().isOk());
    }

    private String bookAtReception(Patient patient, String startsAt) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/appointments").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                                """.formatted(patient.id(), arias.id(), arias.consulta(), startsAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions book(MockHttpSession session, Patient forWhom, String startsAt) throws Exception {
        return mockMvc.perform(post("/portal/booking").session(session).with(csrf())
                .param("reason", arias.consulta().toString()).param("startsAt", startsAt)
                .param("patient", forWhom.id()));
    }

    @Test
    void aParentReadsBooksAndCancelsTheirChildsAppointments() throws Exception {
        Patient father = accounts.withAccount("Diego Luna", "1981-10-10");
        Patient child = accounts.patient("Ema Luna", true, "2016-06-06");
        Patient privateTeen = accounts.patient("Ciro Luna", true, "2011-01-01");
        String group = group("Familia Luna");
        join(group, father, true);
        join(group, privateTeen, false);
        addChild(group, child, father);
        String booked = bookAtReception(child, "2026-03-12T09:00:00");
        String absent = bookAtReception(child, "2026-03-13T09:00:00");
        jdbc.update("update appointment set status = 'ABSENT', starts_at = cast('2026-03-02 09:00:00' as timestamp) "
                + "where id = ?", UUID.fromString(absent));
        bookAtReception(privateTeen, "2026-03-12T11:00:00");
        MockHttpSession session = logIn(father);

        // Mis turnos is the patients' application's (D-111): what it reads, through the same session.
        mockMvc.perform(get("/api/portal/appointments").session(session))
                .andExpect(jsonPath("$.family.length()").value(1))
                .andExpect(jsonPath("$.family[0].fullName").value("Ema Luna"))
                .andExpect(jsonPath("$.family[0].upcoming[0].id").value(booked))
                .andExpect(jsonPath("$.family[0].upcoming[0].cancellable").value(true))
                .andExpect(jsonPath("$.family[0].past[0].state").value("ABSENT"));
        mockMvc.perform(post("/portal/appointments/" + booked + "/cancel").session(session).with(csrf()))
                .andExpect(flash().attribute("message", "Turno cancelado."));
        mockMvc.perform(get("/api/portal/appointments").session(session))
                .andExpect(jsonPath("$.family[0].upcoming[0].state").value("CANCELLED"))
                .andExpect(jsonPath("$.family[0].upcoming[0].cancellationRequestedBy").value("PATIENT"));

        mockMvc.perform(get("/portal/booking").session(session))
                .andExpect(content().string(allOf(containsString(">Diego Luna (vos)<"), containsString(">Ema Luna<"),
                        not(containsString("Ciro Luna")))));
        mockMvc.perform(get("/portal/booking").session(session).param("patient", child.id())
                        .param("reason", arias.consulta().toString()).param("date", "2026-03-14"))
                .andExpect(content().string(allOf(
                        containsString("value=\"" + child.id() + "\" selected=\"selected\">Ema Luna<"),
                        containsString("name=\"patient\" value=\"" + child.id() + "\""))));
        book(session, child, "2026-03-14T10:00:00")
                .andExpect(redirectedUrl("/portal/appointments"))
                .andExpect(flash().attribute("message", containsString("Turno reservado para Ema Luna: sábado")));
        book(session, father, "2026-03-14T11:00:00")
                .andExpect(flash().attribute("message", containsString("Turno reservado: sábado")));
        book(session, father, "2026-03-14T10:00:00")
                .andExpect(redirectedUrl("/portal/booking?reason=" + arias.consulta() + "&date=2026-03-14&patient="
                        + father.id()))
                .andExpect(flash().attribute("error", "Ese horario ya no está libre: elegí otro."));
        book(session, privateTeen, "2026-03-15T10:00:00").andExpect(status().isForbidden());
    }

    @Test
    void aMembersRefusalsNameThem() throws Exception {
        Patient father = accounts.withAccount("Iván Gil", "1979-03-03");
        Patient child = accounts.patient("Zoe Gil", true, "2015-05-05");
        String group = group("Familia Gil");
        join(group, father, true);
        addChild(group, child, father);
        addChild(group, accounts.patient("Noa Gil", true, "2021-01-01"), father);
        bookAtReception(child, "2026-03-16T10:00:00");
        MockHttpSession session = logIn(father);
        mockMvc.perform(get("/api/portal/appointments").session(session))
                .andExpect(jsonPath("$.family[?(@.fullName == 'Noa Gil')].upcoming.length()").value(0));

        // Another practitioner's appointment at the same time keeps her busy.
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        CreatedPractitioner other;
        try {
            other = new PractitionerFixture(mockMvc, json).workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
        mockMvc.perform(post("/api/appointments").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s",
                                 "startsAt": "2026-03-17T10:00:00"}
                                """.formatted(child.id(), other.id(), other.consulta())))
                .andExpect(status().isCreated());
        book(session, child, "2026-03-17T10:00:00")
                .andExpect(flash().attribute("error", "Zoe Gil ya tiene otro turno a esa hora."));

        book(session, child, "2026-03-18T10:00:00").andExpect(flash().attribute("message",
                containsString("para Zoe Gil")));
        book(session, child, "2026-03-19T10:00:00")
                .andExpect(flash().attribute("error",
                        "Zoe Gil ya tiene 3 turnos por delante: para sacar otro, llamá o acercate a recepción."));
        book(session, father, "2026-03-19T10:00:00").andExpect(flash().attribute("message",
                containsString("Turno reservado: ")));
        book(session, father, "2026-03-20T10:00:00").andExpect(status().is3xxRedirection());
        book(session, father, "2026-03-21T10:00:00").andExpect(status().is3xxRedirection());
        book(session, father, "2026-03-22T10:00:00").andExpect(flash().attribute("error",
                "Ya tenés 3 turnos por delante: para sacar otro, llamá o acercate a recepción."));
    }

    @Test
    void myFamilyShowsWhoIsInEachGroupAndLetsThePatientShareOrLeave() throws Exception {
        Patient teen = accounts.withAccount("Abril Vera", "2012-02-02");
        Patient mother = accounts.patient("Paula Vera", true, "1984-04-04");
        Patient child = accounts.patient("Tiago Vera", true, "2019-09-09");
        String group = group("Familia Vera");
        join(group, mother, true);
        join(group, teen, false);
        addChild(group, child, mother);
        Patient uncle = accounts.patient("Luis Vera", true, "1970-07-07");
        join(group, uncle, true);
        mockMvc.perform(put("/api/patients/" + uncle.id() + "/details").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        MockHttpSession session = logIn(teen);
        mockMvc.perform(get("/portal/booking").session(session))
                .andExpect(content().string(allOf(containsString("Profesional y motivo"),
                        not(containsString("name=\"patient\"")))));

        String page = mockMvc.perform(get("/portal/family").session(session))
                .andExpect(content().string(allOf(containsString(">Familia Vera<"),
                        containsString(">Abril Vera (vos)<"),
                        containsString(">Paula Vera<"), containsString(">Luis Vera<"), containsString(">de 13 a 17<"), containsString(">adulto<"),
                        containsString(">menor de 13<"), containsString(">Turnos privados<"),
                        containsString(">Comparte sus turnos<"),
                        containsString(">Los comparte siempre (menor de 13)<"), not(containsString(child.id())))))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).containsPattern("name=\"shares\" value=\"true\">\\s*<button type=\"submit\">\\s*"
                + "Compartir mis turnos<");

        mockMvc.perform(post("/portal/family/" + group + "/sharing").session(session).with(csrf())
                        .param("shares", "true"))
                .andExpect(redirectedUrl("/portal/family"))
                .andExpect(flash().attribute("message", "Listo: tu familia ahora ve tus turnos."));
        mockMvc.perform(get("/portal/family").session(session))
                .andExpect(content().string(allOf(containsString("Dejar de compartir mis turnos"),
                        not(containsString(">Turnos privados<")))));
        mockMvc.perform(post("/portal/family/" + group + "/sharing").session(session).with(csrf())
                        .param("shares", "false"))
                .andExpect(flash().attribute("message", "Listo: tus turnos ya no los ve tu familia."));
        mockMvc.perform(get("/family-groups/" + group).with(RECEPTION))
                .andExpect(content().string(containsString(">el paciente (web)<")));

        mockMvc.perform(post("/portal/family/" + group + "/leave").session(session).with(csrf()))
                .andExpect(flash().attribute("message", "Saliste del grupo."));
        mockMvc.perform(get("/portal/family").session(session))
                .andExpect(content().string(containsString("No estás en ningún grupo familiar.")));
        mockMvc.perform(post("/portal/family/" + group + "/leave").session(session).with(csrf()))
                .andExpect(flash().attribute("error", "Ya no estás en ese grupo."));
        mockMvc.perform(post("/portal/family/" + UUID.randomUUID() + "/sharing").session(session).with(csrf())
                        .param("shares", "true"))
                .andExpect(flash().attribute("error", "Ya no estás en ese grupo."));
    }
}
