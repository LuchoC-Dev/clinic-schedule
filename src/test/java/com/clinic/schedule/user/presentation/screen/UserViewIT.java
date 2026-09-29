package com.clinic.schedule.user.presentation.screen;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

/** The admins' screens (D-68), and what deactivating a user does to the sessions they have open. */
@SpringBootTest
@AutoConfigureMockMvc
class UserViewIT {

    private static final String PASSWORD = "una-clave-larga";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anAdminLandsOnTheUsersAndEveryoneElseOnTheAgenda() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("admin123")).andExpect(redirectedUrl("/users"));
        mockMvc.perform(formLogin("/login").user("recepcion").password("recepcion123")).andExpect(redirectedUrl("/agenda"));
        mockMvc.perform(formLogin("/login").user("arias").password("arias123")).andExpect(redirectedUrl("/agenda"));
    }

    @Test
    @WithUserDetails("admin")
    void theAdminsHeaderOffersTheUsersAlone() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Usuarios<")))
                .andExpect(content().string(containsString("Dra. Laura Arias")))
                .andExpect(content().string(not(containsString(">Agenda<"))))
                .andExpect(content().string(not(containsString(">Pacientes<"))));
    }

    @Test
    @WithUserDetails("admin")
    void anAdminCreatesAUserOnTheScreen() throws Exception {
        String username = fresh();

        String location = mockMvc.perform(post("/users").with(csrf())
                        .param("username", username).param("role", "RECEPTION").param("password", PASSWORD))
                .andExpect(redirectedUrlPattern("/users/*"))
                .andExpect(flash().attribute("message", "Usuario creado: " + username + "."))
                .andReturn().getResponse().getRedirectedUrl();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Recepción")))
                .andExpect(content().string(containsString("Creó el usuario")));
    }

    @Test
    @WithUserDetails("admin")
    void aRefusedUserComesBackWithTheReasonInSpanish() throws Exception {
        mockMvc.perform(post("/users").with(csrf())
                        .param("username", "admin").param("role", "RECEPTION").param("password", PASSWORD))
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("error", containsString("ya se usó")));
        mockMvc.perform(post("/users").with(csrf())
                        .param("username", fresh()).param("role", "RECEPTION").param("password", "corta"))
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("error", containsString("Revisá los datos")));
    }

    @Test
    @WithUserDetails("recepcion")
    void receptionMayNotOpenTheUsers() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void anAdminMayNotOpenReceptionsNorThePractitionersScreens() throws Exception {
        mockMvc.perform(get("/agenda")).andExpect(status().isForbidden());
        mockMvc.perform(get("/patients")).andExpect(status().isForbidden());
        mockMvc.perform(get("/arrivals")).andExpect(status().isForbidden());
        mockMvc.perform(get("/clinical-records/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }

    /** Not when the session runs out: at the very next request (D-68). */
    @Test
    void aDeactivatedUserIsSentOutOfTheSessionTheyHadOpen() throws Exception {
        String username = fresh();
        MockHttpSession admin = logIn("admin", "admin123");
        String location = mockMvc.perform(post("/users").with(csrf()).session(admin)
                        .param("username", username).param("role", "RECEPTION").param("password", PASSWORD))
                .andReturn().getResponse().getRedirectedUrl();
        // The user changes the temporary password first, and logs in again with their own (D-69).
        MockHttpSession first = logIn(username, PASSWORD);
        mockMvc.perform(post("/password").with(csrf()).session(first).param("currentPassword", PASSWORD)
                        .param("newPassword", "la-clave-propia").param("repeatedPassword", "la-clave-propia"))
                .andExpect(redirectedUrl("/login?changed"));
        MockHttpSession theirs = logIn(username, "la-clave-propia");
        mockMvc.perform(get("/agenda").session(theirs)).andExpect(status().isOk());

        mockMvc.perform(post(location + "/deactivate").with(csrf()).session(admin))
                .andExpect(flash().attribute("message", containsString("desactivado")));

        mockMvc.perform(get("/agenda").session(theirs)).andExpect(redirectedUrl("/login?expired"));
        mockMvc.perform(get("/users").session(admin)).andExpect(status().isOk());
    }

    private MockHttpSession logIn(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user(username).password(password))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);
    }

    private static String fresh() {
        return "pantalla-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
