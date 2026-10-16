package com.clinic.schedule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The waiting room's user, {@code sala} in the development data: the screen on the wall, which reads the waiting
 * room and the queues' stream and nothing else, and whose session does not run out (D-70, D-109).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WaitingRoomUserIT {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession logIn(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/api/session").user(username).password(password))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void itsSessionDoesNotRunOut() throws Exception {
        MockHttpSession session = logIn("sala", "sala123");

        assertThat(session.getMaxInactiveInterval()).isEqualTo(-1);
        mockMvc.perform(get("/api/waiting-room").session(session)).andExpect(status().isOk());
    }

    /** Anyone else's session runs out as it always did. */
    @Test
    void receptionsSessionStillRunsOut() throws Exception {
        assertThat(logIn("recepcion", "recepcion123").getMaxInactiveInterval()).isNotEqualTo(-1);
    }

    @Test
    @WithUserDetails("sala")
    void itListensToTheQueues() throws Exception {
        mockMvc.perform(get("/api/queue-events")).andExpect(request().asyncStarted());
    }

    @Test
    @WithUserDetails("sala")
    void itReachesNothingElseOnTheApi() throws Exception {
        for (String route : new String[] {"/api/queues", "/api/patients", "/api/appointments", "/api/agenda",
                "/api/my-queue", "/api/clinical-records/" + UUID.randomUUID(), "/api/users"}) {
            mockMvc.perform(get(route)).andExpect(status().isForbidden());
        }
    }

    /** The queues' stream is reception's, the practitioners' and the wall's: an admin's screens follow no queue. */
    @Test
    @WithUserDetails("admin")
    void anAdminDoesNotListenToTheQueues() throws Exception {
        mockMvc.perform(get("/api/queue-events")).andExpect(status().isForbidden());
    }
}
