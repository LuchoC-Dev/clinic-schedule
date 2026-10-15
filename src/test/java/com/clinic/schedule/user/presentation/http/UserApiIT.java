package com.clinic.schedule.user.presentation.http;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An admin administers the practice's users through the API (D-68), as the seeded {@code admin}.
 * Every test makes users of its own, with names nobody else takes, and — unless the test is about it —
 * has each one change the temporary password the admin gave them, as they would before anything else
 * (D-69).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserApiIT {

    private static final RequestPostProcessor ADMIN = httpBasic("admin", "admin123");
    private static final RequestPostProcessor RECEPTION = httpBasic("recepcion", "recepcion123");
    private static final String PASSWORD = "una-clave-larga";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void aUserCreatedByAnAdminLogsInAndTheCreationIsRecorded() throws Exception {
        String username = fresh("recepcion");

        JsonNode created = create(username, "RECEPTION", null, PASSWORD);

        mockMvc.perform(get("/api/patients").with(httpBasic(username, PASSWORD))).andExpect(status().isOk());
        mockMvc.perform(get("/api/users/" + created.get("id").asText() + "/changes").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'CREATED')].changedBy", hasItem("admin")));
    }

    @Test
    void theAnswerNeverCarriesThePassword() throws Exception {
        mockMvc.perform(post("/api/users").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body(fresh("recepcion"), "RECEPTION", null, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mockMvc.perform(get("/api/users").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].passwordHash").isEmpty());
    }

    @Test
    void aPractitionersUserActsAsThatPractitioner() throws Exception {
        UUID practitioner = practitioner();
        String username = fresh("profesional");
        create(username, "PRACTITIONER", practitioner, PASSWORD);

        mockMvc.perform(get("/api/appointments").with(httpBasic(username, PASSWORD))
                        .param("practitionerId", practitioner.toString()).param("date", "2026-03-16"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/patients").with(httpBasic(username, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPractitionerHasOneActiveUserAtMost() throws Exception {
        UUID practitioner = practitioner();
        JsonNode first = create(fresh("profesional"), "PRACTITIONER", practitioner, PASSWORD);
        String second = fresh("profesional");

        attempt(second, "PRACTITIONER", practitioner).andExpect(status().isConflict());

        // Once the first is deactivated, the practitioner is free for another, and the first can no
        // longer come back while the second is active.
        deactivate(first.get("id").asText()).andExpect(status().isOk());
        create(second, "PRACTITIONER", practitioner, PASSWORD);
        mockMvc.perform(post("/api/users/" + first.get("id").asText() + "/reactivate").with(ADMIN))
                .andExpect(status().isConflict());
    }

    @Test
    void theFormOfAUserIsCheckedBeforeAnythingIsSaved() throws Exception {
        attempt("Con Espacios", "RECEPTION", null).andExpect(status().isBadRequest());
        attempt(fresh("sinprofesional"), "PRACTITIONER", null).andExpect(status().isBadRequest());
        attempt(fresh("recepcion"), "RECEPTION", practitioner()).andExpect(status().isBadRequest());
        attempt(fresh("desconocido"), "PRACTITIONER", UUID.randomUUID()).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body(fresh("corta"), "RECEPTION", null, "corta")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aUsernameIsNeverUsedTwiceNotEvenAfterItsUserWasDeactivated() throws Exception {
        String username = fresh("recepcion");
        JsonNode created = create(username, "RECEPTION", null, PASSWORD);
        deactivate(created.get("id").asText()).andExpect(status().isOk());

        attempt(username, "RECEPTION", null).andExpect(status().isConflict());
    }

    @Test
    void aDeactivatedUserCannotLogInUntilReactivated() throws Exception {
        String username = fresh("recepcion");
        String id = create(username, "RECEPTION", null, PASSWORD).get("id").asText();

        deactivate(id).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/patients").with(httpBasic(username, PASSWORD))).andExpect(status().isUnauthorized());
        deactivate(id).andExpect(status().isConflict());

        mockMvc.perform(post("/api/users/" + id + "/reactivate").with(ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/patients").with(httpBasic(username, PASSWORD))).andExpect(status().isOk());
        mockMvc.perform(get("/api/users/" + id + "/changes").with(ADMIN))
                .andExpect(jsonPath("$[*].action", hasItem("DEACTIVATED")))
                .andExpect(jsonPath("$[*].action", hasItem("REACTIVATED")));
    }

    @Test
    void anAdminCannotDeactivateThemselves() throws Exception {
        String username = fresh("admin");
        String id = create(username, "ADMIN", null, PASSWORD).get("id").asText();

        mockMvc.perform(post("/api/users/" + id + "/deactivate").with(httpBasic(username, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void aResetPasswordReplacesTheOldOne() throws Exception {
        String username = fresh("recepcion");
        String id = create(username, "RECEPTION", null, PASSWORD).get("id").asText();

        mockMvc.perform(post("/api/users/" + id + "/password-reset").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "otra-clave-larga"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/patients").with(httpBasic(username, PASSWORD))).andExpect(status().isUnauthorized());
        // The new one is temporary, as the first was: the user changes it before anything else.
        mockMvc.perform(get("/api/patients").with(httpBasic(username, "otra-clave-larga"))).andExpect(status().isForbidden());
        changeOwnPassword(username, "otra-clave-larga", "clave-propia-nueva");
        mockMvc.perform(get("/api/patients").with(httpBasic(username, "clave-propia-nueva"))).andExpect(status().isOk());
        // The tests' clock stands still, so the history is checked for the entry, not for its place.
        mockMvc.perform(get("/api/users/" + id + "/changes").with(ADMIN))
                .andExpect(jsonPath("$[*].action", hasItem("PASSWORD_RESET")));
    }

    @Test
    void anUnknownUserIsNotFound() throws Exception {
        mockMvc.perform(post("/api/users/" + UUID.randomUUID() + "/deactivate").with(ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAnAdminAdministersUsers() throws Exception {
        mockMvc.perform(get("/api/users").with(RECEPTION)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").with(httpBasic("arias", "arias123"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users").with(RECEPTION).contentType(MediaType.APPLICATION_JSON)
                        .content(body(fresh("recepcion"), "ADMIN", null, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    /** The admins' screen offers only the practitioners a new user may stand for (D-68, D-107). */
    @Test
    void thePractitionersOfferedForANewUserAreThoseWithNoActiveUser() throws Exception {
        UUID taken = practitioner();
        UUID free = practitioner();
        create(fresh("profesional"), "PRACTITIONER", taken, PASSWORD);

        mockMvc.perform(get("/api/users/practitioners-without-user").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(free.toString())))
                .andExpect(jsonPath("$[?(@.id == '%s')].fullName".formatted(free), hasItem("Dra. Usuaria")))
                .andExpect(jsonPath("$[*].id", not(hasItem(taken.toString()))));
        mockMvc.perform(get("/api/users/practitioners-without-user").with(RECEPTION)).andExpect(status().isForbidden());
    }

    /** The admins' screens name the practitioners: they read the list, and change nothing of it (D-107). */
    @Test
    void anAdminReadsThePractitionersListAndNothingMoreOfThem() throws Exception {
        UUID practitioner = practitioner();

        mockMvc.perform(get("/api/practitioners").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(practitioner.toString())));
        mockMvc.perform(get("/api/practitioners/" + practitioner).with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/practitioners").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Dr. Admin"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/practitioners").with(httpBasic("arias", "arias123"))).andExpect(status().isForbidden());
    }

    /** The admin reads no clinical content, and does none of reception's work (D-68). */
    @Test
    void anAdminReachesNothingButTheUsers() throws Exception {
        mockMvc.perform(get("/api/patients").with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/appointments").with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/queues").with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clinical-records/" + UUID.randomUUID()).with(ADMIN)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("admin")))
                .andExpect(jsonPath("$[*].username", not(hasItem("nadie"))));
    }

    /** A user created by the admin with a temporary password, who then changes it to {@code password}. */
    private JsonNode create(String username, String role, UUID practitionerId, String password) throws Exception {
        String temporary = "temporal-" + password;
        JsonNode created = json.readTree(mockMvc.perform(post("/api/users").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, role, practitionerId, temporary)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        changeOwnPassword(username, temporary, password);
        return created;
    }

    private void changeOwnPassword(String username, String current, String next) throws Exception {
        mockMvc.perform(post("/api/password").with(httpBasic(username, current)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "%s"}
                                """.formatted(current, next)))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions attempt(String username, String role, UUID practitionerId)
            throws Exception {
        return mockMvc.perform(post("/api/users").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(body(username, role, practitionerId, PASSWORD)));
    }

    private org.springframework.test.web.servlet.ResultActions deactivate(String id) throws Exception {
        return mockMvc.perform(post("/api/users/" + id + "/deactivate").with(ADMIN));
    }

    private UUID practitioner() throws Exception {
        return UUID.fromString(json.readTree(mockMvc.perform(post("/api/practitioners").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Dra. Usuaria"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private static String body(String username, String role, UUID practitionerId, String password) {
        return """
                {"username": "%s", "role": "%s", "practitionerId": %s, "password": "%s"}
                """.formatted(username, role, practitionerId == null ? "null" : "\"" + practitionerId + "\"", password);
    }

    private static String fresh(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
