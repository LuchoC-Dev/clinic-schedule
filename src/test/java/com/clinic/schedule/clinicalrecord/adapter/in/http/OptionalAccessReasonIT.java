package com.clinic.schedule.clinicalrecord.adapter.in.http;

import static com.clinic.schedule.Logins.staffUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A practice whose law does not require a reason to ask for access to a record (D-54): the practitioners' screens are
 * told so, and say the reason is optional (D-105).
 */
@SpringBootTest(properties = "app.clinical-record.access-grant.reason-required=false")
@AutoConfigureMockMvc
class OptionalAccessReasonIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void thePractitionersAreToldTheReasonIsOptional() throws Exception {
        mockMvc.perform(get("/api/access-grants/settings").with(staffUser("arias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonRequired").value(false));
    }
}
