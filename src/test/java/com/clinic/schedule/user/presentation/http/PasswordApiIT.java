package com.clinic.schedule.user.presentation.http;

import static com.clinic.schedule.Logins.staffLogIn;
import static com.clinic.schedule.Logins.staff;
import static com.clinic.schedule.Logins.staffUser;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Changing one's own password through the API, and the temporary password an admin sets, which must
 * be changed before anything else (D-69).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordApiIT {

    private static final String TEMPORARY = "temporal-del-admin";
    private static final String OWN = "la-clave-propia";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void aTemporaryPasswordOpensNothingButChangingIt() throws Exception {
        String username = created();

        mockMvc.perform(get("/api/patients").with(staff(mockMvc, username, TEMPORARY)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("password must be changed")));

        change(username, TEMPORARY, OWN).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/patients").with(staff(mockMvc, username, OWN))).andExpect(status().isOk());
        staffLogIn(mockMvc, username, TEMPORARY).andExpect(status().isUnauthorized());
    }

    @Test
    void theChangeIsRecordedAsTheUsersOwn() throws Exception {
        String username = created();
        change(username, TEMPORARY, OWN);

        mockMvc.perform(get("/api/users/" + idOf(username) + "/changes").with(staffUser("admin")))
                .andExpect(jsonPath("$[?(@.action == 'PASSWORD_CHANGED')].changedBy", hasItem(username)));
    }

    @Test
    void theCurrentPasswordMustBeTheirs() throws Exception {
        String username = created();

        change(username, TEMPORARY, "otra-cualquiera", OWN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("current password is wrong")));
    }

    @Test
    void theNewPasswordFollowsThePolicyAndIsNotTheSame() throws Exception {
        String username = created();

        change(username, TEMPORARY, "corta").andExpect(status().isBadRequest());
        change(username, TEMPORARY, TEMPORARY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("must differ")));
    }

    /** The users of the development data, and those from before, have no temporary password. */
    @Test
    void aSeededUserIsNotHeldBack() throws Exception {
        mockMvc.perform(get("/api/patients").with(staffUser("recepcion"))).andExpect(status().isOk());
    }

    private String created() throws Exception {
        String username = "clave-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/users").with(staffUser("admin")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "role": "RECEPTION", "password": "%s"}
                                """.formatted(username, TEMPORARY)))
                .andExpect(status().isCreated());
        return username;
    }

    private ResultActions change(String username, String loggedInWith, String next) throws Exception {
        return change(username, loggedInWith, loggedInWith, next);
    }

    private ResultActions change(String username, String loggedInWith, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/password").with(staff(mockMvc, username, loggedInWith))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(current, next)));
    }

    private String idOf(String username) throws Exception {
        for (var user : json.readTree(mockMvc.perform(get("/api/users").with(staffUser("admin")))
                .andReturn().getResponse().getContentAsString())) {
            if (user.get("username").asText().equals(username)) {
                return user.get("id").asText();
            }
        }
        throw new AssertionError("No user " + username);
    }
}
