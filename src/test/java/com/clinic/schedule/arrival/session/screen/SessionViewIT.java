package com.clinic.schedule.arrival.session.screen;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.clinicalrecord.PractitionerUserFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reception's arrivals screen and the practitioner's queue over the real stack (D-63), in Spanish. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class SessionViewIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserDetailsService userDetails;

    private String queue;
    private String screen;
    private String name;
    private RequestPostProcessor asArias;

    private String idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        name = "Paciente " + UUID.randomUUID().toString().substring(0, 8);
        String arias = idOf(mockMvc.perform(post("/api/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Dra. Pantalla\"}")));
        asArias = new PractitionerUserFixture(jdbc, userDetails).userFor(UUID.fromString(arias));
        queue = idOf(mockMvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Cola " + UUID.randomUUID() + "\"}")));
        mockMvc.perform(post("/api/queues/" + queue + "/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"practitionerId\": \"" + arias + "\"}"));
        screen = "/arrivals?queueId=" + queue;
    }

    private String patient() throws Exception {
        return idOf(mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"" + name + "\"}")));
    }

    @Test
    void receptionOpensTheQueueFindsAPatientAndBringsThemIn() throws Exception {
        String patient = patient();
        mockMvc.perform(get(screen)).andExpect(content().string(containsString("Cerrada")));

        mockMvc.perform(post("/arrivals/" + queue + "/open").with(csrf()))
                .andExpect(redirectedUrl(screen)).andExpect(flash().attribute("message", "Cola abierta."));
        mockMvc.perform(get(screen).param("query", name))
                .andExpect(content().string(containsString("Nadie espera.")))
                .andExpect(content().string(containsString("Sumar como urgencia")));
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf())
                        .param("patientId", patient).param("emergency", "true"))
                .andExpect(flash().attribute("message", "Paciente sumado como urgencia."));
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", patient)
                        .param("amount", "15000"))
                .andExpect(flash().attribute("error", "Ese paciente ya está esperando en esta cola."));

        mockMvc.perform(get(screen))
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString("<strong>Urgencia</strong>")));
    }

    @Test
    void aClosedQueueTakesNobodyIn() throws Exception {
        mockMvc.perform(post("/arrivals/" + queue + "/unidentified").with(csrf()).param("amount", "15000"))
                .andExpect(flash().attribute("error", "La cola está cerrada: abrila primero."));
    }

    @Test
    void thePractitionerCallsFinishesAndSaysWhoDidNotComeIn() throws Exception {
        mockMvc.perform(post("/arrivals/" + queue + "/open").with(csrf()));
        mockMvc.perform(post("/arrivals/" + queue + "/patients").with(csrf()).param("patientId", patient())
                .param("amount", "15000"));

        mockMvc.perform(get("/my-queue").with(asArias))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Llamar siguiente")))
                .andExpect(content().string(containsString(name)));
        mockMvc.perform(post("/my-queue/call-next").with(asArias).with(csrf())).andExpect(redirectedUrl("/my-queue"));
        mockMvc.perform(get("/my-queue").with(asArias))
                .andExpect(content().string(containsString("Llamaste a")))
                .andExpect(content().string(containsString("Empezar consulta")))
                .andExpect(content().string(containsString("No ingresó")));
        mockMvc.perform(post("/my-queue/call-next").with(asArias).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Terminá con el paciente llamado")));

        mockMvc.perform(post("/my-queue/did-not-come-in").with(asArias).with(csrf()));
        mockMvc.perform(get("/my-queue").with(asArias))
                .andExpect(content().string(not(containsString("Llamaste a"))))
                .andExpect(content().string(containsString(name)));
        mockMvc.perform(get(screen)).andExpect(content().string(containsString("Llamado 1 vez")));
    }

    @Test
    void aPractitionerServingNoQueueIsToldSo() throws Exception {
        String other = idOf(mockMvc.perform(post("/api/practitioners").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"Dr. Sin Cola\"}")));

        mockMvc.perform(get("/my-queue").with(new PractitionerUserFixture(jdbc, userDetails).userFor(UUID.fromString(other))))
                .andExpect(content().string(containsString("No atendés ninguna cola.")));
    }
}
