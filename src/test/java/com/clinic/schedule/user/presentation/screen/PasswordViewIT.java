package com.clinic.schedule.user.presentation.screen;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The password screen, and a temporary password keeping its user there until they change it (D-69). */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordViewIT {

    private static final String TEMPORARY = "temporal-del-admin";
    private static final String OWN = "la-clave-propia";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aTemporaryPasswordLandsOnChangingItAndKeepsTheUserThere() throws Exception {
        String username = created();

        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user(username).password(TEMPORARY))
                .andExpect(redirectedUrl("/password?required"))
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/agenda").session(session)).andExpect(redirectedUrl("/password?required"));
        mockMvc.perform(get("/patients").session(session)).andExpect(redirectedUrl("/password?required"));
        mockMvc.perform(get("/password").session(session).param("required", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tu contraseña es temporal")));
    }

    @Test
    void changingItClosesTheSessionAndTheNewOneLetsTheUserIn() throws Exception {
        String username = created();
        MockHttpSession session = logIn(username, TEMPORARY);

        change(session, TEMPORARY, OWN, OWN).andExpect(redirectedUrl("/login?changed"));

        mockMvc.perform(get("/agenda").session(session)).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(formLogin("/login").user(username).password(TEMPORARY)).andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(formLogin("/login").user(username).password(OWN)).andExpect(redirectedUrl("/agenda"));
    }

    @Test
    void aRefusedChangeComesBackWithTheReasonInSpanish() throws Exception {
        MockHttpSession session = logIn(created(), TEMPORARY);

        change(session, "otra-cualquiera", OWN, OWN)
                .andExpect(redirectedUrl("/password"))
                .andExpect(flash().attribute("error", containsString("actual no es correcta")));
        change(session, TEMPORARY, OWN, "otra-distinta")
                .andExpect(redirectedUrl("/password"))
                .andExpect(flash().attribute("error", containsString("no coinciden")));
        change(session, TEMPORARY, TEMPORARY, TEMPORARY)
                .andExpect(redirectedUrl("/password"))
                .andExpect(flash().attribute("error", containsString("distinta de la actual")));
        change(session, TEMPORARY, "corta", "corta")
                .andExpect(redirectedUrl("/password"))
                .andExpect(flash().attribute("error", containsString("caracteres")));
    }

    /** Anyone may change theirs, from the link every header offers. */
    @Test
    void everyHeaderOffersTheChange() throws Exception {
        MockHttpSession reception = logIn("recepcion", "recepcion123");
        mockMvc.perform(get("/agenda").session(reception))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Contraseña<")));
        MockHttpSession admin = logIn("admin", "admin123");
        mockMvc.perform(get("/users").session(admin))
                .andExpect(content().string(containsString(">Contraseña<")));
        mockMvc.perform(get("/password").session(admin)).andExpect(status().isOk());
    }

    private ResultActions change(MockHttpSession session, String current, String next, String repeated) throws Exception {
        return mockMvc.perform(post("/password").with(csrf()).session(session)
                .param("currentPassword", current).param("newPassword", next).param("repeatedPassword", repeated));
    }

    private String created() throws Exception {
        String username = "pantalla-clave-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/users").with(httpBasic("admin", "admin123")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "role": "RECEPTION", "password": "%s"}
                                """.formatted(username, TEMPORARY)))
                .andExpect(status().isCreated());
        return username;
    }

    private MockHttpSession logIn(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user(username).password(password))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);
    }
}
