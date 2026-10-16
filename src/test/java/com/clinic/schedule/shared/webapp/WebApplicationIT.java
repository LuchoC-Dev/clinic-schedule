package com.clinic.schedule.shared.webapp;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The two applications as Maven builds them and Spring serves them (D-97, D-114): the staff's at {@code /}, the
 * patients' at {@code /portal/}, open to anyone, since what they show is asked of the API with a login; each address
 * of their own answered with their page; and never kept by the browser's cache.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    /** The staff's page: its files from the root, none of the patients'. */
    private static Matcher<String> staffsPage() {
        return allOf(containsString("<div id=\"root\">"), containsString("src=\"/assets/"),
                not(containsString("/portal/assets/")));
    }

    private static Matcher<String> patientsPage() {
        return allOf(containsString("<div id=\"root\">"), containsString("src=\"/portal/assets/"));
    }

    @Test
    void theStaffsApplicationAnswersItsOwnAddressesWithItsPage() throws Exception {
        for (String address : new String[] {"/login", "/agenda", "/waiting-room", "/clinical-records/a1"}) {
            mockMvc.perform(get(address))
                    .andExpect(status().isOk())
                    .andExpect(content().string(staffsPage()))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }
        mockMvc.perform(get("/")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void thePatientsApplicationAnswersItsOwnAddressesWithItsPage() throws Exception {
        for (String address : new String[] {"/portal/login", "/portal/booking", "/portal/family"}) {
            mockMvc.perform(get(address))
                    .andExpect(status().isOk())
                    .andExpect(content().string(patientsPage()))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }
        mockMvc.perform(get("/portal/")).andExpect(forwardedUrl("/portal/index.html"));
        mockMvc.perform(get("/portal")).andExpect(redirectedUrl("/portal/"));
    }

    /**
     * The addresses the emails carry — sent ones too — are the patients' application's own: each opens its screen,
     * the link's token in the address, with nobody logged in (D-110, D-111, D-114).
     */
    @Test
    void theEmailsAddressesOpenThePatientsApplication() throws Exception {
        for (String path : new String[] {"/activation", "/password-reset", "/register/confirm", "/cancel"}) {
            mockMvc.perform(get("/portal" + path).param("token", "Ab-9_x"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(patientsPage()));
        }
        // Mis turnos, which the booking's email points at, has no token (D-111).
        mockMvc.perform(get("/portal/appointments")).andExpect(status().isOk())
                .andExpect(content().string(patientsPage()));
    }

    /** A file the build did not make is a 404, not the page: a broken link stays visible. */
    @Test
    void aFileThatIsNotThereIsA404() throws Exception {
        mockMvc.perform(get("/assets/no-esta.js")).andExpect(status().isNotFound());
        mockMvc.perform(get("/portal/assets/no-esta.css")).andExpect(status().isNotFound());
    }

    /** The API's addresses are never a page: one no controller answers is the API's 404. */
    @Test
    void anAddressOfTheApiThatIsNotThereIsNoPage() throws Exception {
        mockMvc.perform(get("/api/no-esta").with(user("recepcion").roles("RECEPTION")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<div id=\"root\">"))));
        mockMvc.perform(get("/api/portal/no-esta").with(user("DNI 1").roles("PATIENT")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("<div id=\"root\">"))));
    }
}
