package com.clinic.schedule.arrival.setup.screen;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Reception's queue screens over the real stack (D-62), with their refusals in Spanish. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class QueueViewIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private String name;

    @BeforeEach
    void setUp() {
        name = "Cola " + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID practitioner(String fullName) throws Exception {
        return UUID.fromString(json.readTree(mockMvc.perform(post("/api/practitioners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"" + fullName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private String createdQueue() throws Exception {
        return mockMvc.perform(post("/queues").with(csrf()).param("name", name).param("room", "3"))
                .andExpect(redirectedUrlPattern("/queues/*"))
                .andExpect(flash().attribute("message", "Cola creada."))
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    void aQueueIsSetUpAndServedFromTheScreens() throws Exception {
        String screen = createdQueue();
        UUID practitioner = practitioner("Dra. Colas " + name);

        mockMvc.perform(get(screen))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Consultorio 3")))
                .andExpect(content().string(containsString("Nadie atiende esta cola.")));

        mockMvc.perform(post(screen + "/practitioners").with(csrf()).param("practitionerId", practitioner.toString()))
                .andExpect(redirectedUrl(screen))
                .andExpect(flash().attribute("message", "Profesional agregado a la cola."));
        mockMvc.perform(get("/queues"))
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString("Dra. Colas " + name)));

        mockMvc.perform(post(screen + "/practitioners/" + practitioner + "/remove").with(csrf()))
                .andExpect(redirectedUrl(screen))
                .andExpect(flash().attribute("message", "Profesional quitado de la cola."));
        mockMvc.perform(get(screen)).andExpect(content().string(containsString("Nadie atiende esta cola.")));
    }

    @Test
    void aPractitionerServingAnotherQueueIsNotOfferedAndIsRefusedInSpanish() throws Exception {
        String first = createdQueue();
        name = "Otra " + name;
        String second = createdQueue();
        UUID practitioner = practitioner("Dr. Ocupado " + name);
        mockMvc.perform(post(first + "/practitioners").with(csrf()).param("practitionerId", practitioner.toString()));

        mockMvc.perform(get(second)).andExpect(content().string(not(containsString("Dr. Ocupado " + name))));
        mockMvc.perform(post(second + "/practitioners").with(csrf()).param("practitionerId", practitioner.toString()))
                .andExpect(redirectedUrl(second))
                .andExpect(flash().attribute("error", startsWith("Ese profesional ya atiende otra cola")));
    }

    @Test
    void aTakenNameComesBackAsAMessage() throws Exception {
        createdQueue();

        mockMvc.perform(post("/queues").with(csrf()).param("name", name.toUpperCase()))
                .andExpect(redirectedUrl("/queues"))
                .andExpect(flash().attribute("error", "Ya hay otra cola con ese nombre."));
    }

    @Test
    @WithMockUser(roles = "PRACTITIONER")
    void aPractitionerDoesNotSeeTheQueueScreens() throws Exception {
        mockMvc.perform(get("/queues")).andExpect(status().isForbidden());
    }
}
