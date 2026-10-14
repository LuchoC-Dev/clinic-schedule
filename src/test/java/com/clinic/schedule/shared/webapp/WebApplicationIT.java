package com.clinic.schedule.shared.webapp;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The two applications as Maven builds them and Spring serves them (D-97): open to anyone, since what they
 * show is asked of the API with a login; each address of their own answered with their page; and never kept by
 * the browser's cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theStaffsApplicationAnswersItsOwnAddressesWithItsPage() throws Exception {
        for (String address : new String[] {"/app/login", "/app/agenda"}) {
            mockMvc.perform(get(address))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("<div id=\"root\">"),
                            containsString("/app/assets/"), not(containsString("/portal/app/assets/")))))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }
        mockMvc.perform(get("/app/")).andExpect(forwardedUrl("/app/index.html"));
        mockMvc.perform(get("/app")).andExpect(redirectedUrl("/app/"));
    }

    @Test
    void thePatientsApplicationAnswersItsOwnAddressesWithItsPage() throws Exception {
        for (String address : new String[] {"/portal/app/login", "/portal/app/turnos"}) {
            mockMvc.perform(get(address))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(containsString("<div id=\"root\">"),
                            containsString("/portal/app/assets/"))))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }
        mockMvc.perform(get("/portal/app/")).andExpect(forwardedUrl("/portal/app/index.html"));
        mockMvc.perform(get("/portal/app")).andExpect(redirectedUrl("/portal/app/"));
    }

    /** A file the build did not make is a 404, not the page: a broken link stays visible. */
    @Test
    void aFileThatIsNotThereIsA404() throws Exception {
        mockMvc.perform(get("/app/assets/no-esta.js")).andExpect(status().isNotFound());
        mockMvc.perform(get("/portal/app/assets/no-esta.css")).andExpect(status().isNotFound());
    }

    /** The screens keep their addresses while they move over (D-02). */
    @Test
    void theThymeleafScreensKeepTheirAddresses() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<div id=\"root\">"))));
        mockMvc.perform(get("/portal/login")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<div id=\"root\">"))));
        mockMvc.perform(get("/portal/activation").param("token", "x")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<div id=\"root\">"))));
    }
}
