package com.clinic.schedule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
 * The waiting room's user, {@code sala} in the development data: the screen on the wall, which sees
 * the waiting-room screen and its stream and nothing else, and whose session does not run out (D-70).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WaitingRoomUserIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void itLandsOnTheWaitingRoomWithASessionThatDoesNotRunOut() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("sala").password("sala123"))
                .andExpect(redirectedUrl("/waiting-room"))
                .andReturn().getRequest().getSession(false);

        assertThat(session.getMaxInactiveInterval()).isEqualTo(-1);
        mockMvc.perform(get("/waiting-room").session(session)).andExpect(status().isOk());
    }

    /** Anyone else's session runs out as it always did. */
    @Test
    void receptionsSessionStillRunsOut() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("recepcion").password("recepcion123"))
                .andReturn().getRequest().getSession(false);

        assertThat(session.getMaxInactiveInterval()).isNotEqualTo(-1);
    }

    @Test
    @WithUserDetails("sala")
    void itListensToTheQueues() throws Exception {
        mockMvc.perform(get("/queue-events")).andExpect(request().asyncStarted());
    }

    @Test
    @WithUserDetails("sala")
    void itSeesNothingElse() throws Exception {
        for (String screen : new String[] {"/", "/agenda", "/patients", "/arrivals", "/queues", "/my-queue",
                "/checks/pending", "/clinical-records/" + UUID.randomUUID(), "/users"}) {
            mockMvc.perform(get(screen)).andExpect(status().isForbidden());
        }
    }

    @Test
    void itReachesNothingOnTheApi() throws Exception {
        for (String route : new String[] {"/api/queues", "/api/patients", "/api/appointments", "/api/my-queue",
                "/api/users"}) {
            mockMvc.perform(get(route).with(httpBasic("sala", "sala123"))).andExpect(status().isForbidden());
        }
    }

    /** Reception may still open it, on any screen, as before. */
    @Test
    @WithUserDetails("recepcion")
    void receptionMayStillOpenTheWaitingRoom() throws Exception {
        mockMvc.perform(get("/waiting-room")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerMayNotOpenIt() throws Exception {
        mockMvc.perform(get("/waiting-room")).andExpect(status().isForbidden());
    }
}
