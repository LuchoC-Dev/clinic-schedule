package com.clinic.schedule.practitioner.presentation.screen;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Reception's practitioner screens over the real stack (D-46): thin, so what is worth checking
 * is that they reach the service and say the right thing in Spanish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class PractitionerViewIT {

    @Autowired
    private MockMvc mockMvc;

    private static String unique(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 6);
    }

    /** Registers a practitioner through the screen and answers the page it lands on. */
    private String registered(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/practitioners").with(csrf()).param("fullName", name))
                .andExpect(redirectedUrlPattern("/practitioners/*"))
                .andExpect(flash().attribute("message", "Profesional registrado. Cargá sus razones y su semana."))
                .andReturn();
        return result.getResponse().getRedirectedUrl();
    }

    @Test
    void theListShowsEveryPractitionerWithTheWayToConfigureThem() throws Exception {
        String name = unique("Dr. Lista");
        String page = registered(name);

        mockMvc.perform(get("/practitioners"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString("href=\"" + page + "\"")));
    }

    @Test
    void aNewPractitionerOpensWithNothingConfigured() throws Exception {
        String page = registered(unique("Dra. Nueva"));

        mockMvc.perform(get(page))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no tiene razones")))
                .andExpect(content().string(containsString("No tiene ausencias próximas.")));
    }

    @Test
    void aNameTooLongComesBackAsAMessageInsteadOfFailing() throws Exception {
        mockMvc.perform(post("/practitioners").with(csrf()).param("fullName", "x".repeat(200)))
                .andExpect(redirectedUrl("/practitioners"))
                .andExpect(flash().attribute("error", containsString("hasta 150 caracteres")));
    }

    @Test
    void renamingIsSaved() throws Exception {
        String page = registered(unique("Dr. Viejo"));
        String renamed = unique("Dr. Nuevo");

        mockMvc.perform(post(page + "/name").with(csrf()).param("fullName", renamed))
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("message", "Nombre guardado."));

        mockMvc.perform(get(page)).andExpect(content().string(containsString(renamed)));
    }

    @Test
    void reasonsAreAddedChangedAndARepeatedOneRefused() throws Exception {
        String page = registered(unique("Dra. Razones"));

        mockMvc.perform(post(page + "/reasons").with(csrf()).param("name", "consulta").param("durationMinutes", "30"))
                .andExpect(flash().attribute("message", "Razón agregada."));
        mockMvc.perform(post(page + "/reasons").with(csrf()).param("name", "Consulta").param("durationMinutes", "20"))
                .andExpect(flash().attribute("error", "Ya tiene una razón con ese nombre."));
        mockMvc.perform(post(page + "/reasons").with(csrf()).param("name", "eterna").param("durationMinutes", "481"))
                .andExpect(flash().attribute("error", containsString("entre 1 y 480 minutos")));

        String html = mockMvc.perform(get(page)).andReturn().getResponse().getContentAsString();
        String reasonAction = html.replaceAll("(?s).*action=\"(" + page + "/reasons/[^\"]+)\".*", "$1");

        mockMvc.perform(post(reasonAction).with(csrf()).param("name", "consulta larga").param("durationMinutes", "45"))
                .andExpect(flash().attribute("message", "Razón guardada."));
        mockMvc.perform(get(page))
                .andExpect(content().string(containsString("value=\"consulta larga\"")))
                .andExpect(content().string(containsString("value=\"45\"")));
    }

    @Test
    void theWeekIsSavedWholeAndAnEmptyRowIsDropped() throws Exception {
        String page = registered(unique("Dra. Semana"));

        mockMvc.perform(post(page + "/weekly-schedule").with(csrf())
                        .param("day", "MONDAY", "MONDAY", "")
                        .param("startsAt", "09:00", "14:00", "")
                        .param("endsAt", "13:00", "18:00", ""))
                .andExpect(redirectedUrl(page))
                .andExpect(flash().attribute("message", "Semana guardada."));

        mockMvc.perform(get(page))
                .andExpect(content().string(containsString("value=\"09:00\"")))
                .andExpect(content().string(containsString("value=\"14:00\"")))
                .andExpect(content().string(containsString("<option value=\"MONDAY\" selected=\"selected\">Lunes</option>")));
    }

    @Test
    void stretchesThatMeetAreRefusedInSpanish() throws Exception {
        String page = registered(unique("Dra. Tramos"));

        mockMvc.perform(post(page + "/weekly-schedule").with(csrf())
                        .param("day", "MONDAY", "MONDAY")
                        .param("startsAt", "09:00", "13:00")
                        .param("endsAt", "13:00", "18:00"))
                .andExpect(flash().attribute("error", containsString("se superponen o se tocan")));
    }

    @Test
    void aHalfFilledRowIsRefusedRatherThanGuessed() throws Exception {
        String page = registered(unique("Dra. Fila"));

        mockMvc.perform(post(page + "/weekly-schedule").with(csrf())
                        .param("day", "TUESDAY")
                        .param("startsAt", "09:00")
                        .param("endsAt", ""))
                .andExpect(flash().attribute("error", containsString("Completá día, desde y hasta")));
        mockMvc.perform(get(page)).andExpect(content().string(not(containsString("value=\"09:00\""))));
    }

    @Test
    void anUnknownPractitionerLeadsBackToTheList() throws Exception {
        mockMvc.perform(get("/practitioners/" + UUID.randomUUID()))
                .andExpect(redirectedUrl("/practitioners"))
                .andExpect(flash().attribute("error", "No se encontró el profesional."));
    }
}
