package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

/** The admins' screen of the refused attempts to reach clinical content (D-72). */
@SpringBootTest
@AutoConfigureMockMvc
class RefusalLogViewIT {

    /** Ana Pérez, of the development data. */
    private static final String ANA = "c3333333-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithUserDetails("admin")
    void theAdminSeesWhoWasRefusedWhatInSpanish() throws Exception {
        jdbc.update("insert into record_refusal (id, refused_by, practitioner_id, refused_at, attempt, patient_id, route) "
                        + "values (?, 'recepcion', null, ?, 'ROUTE', ?, ?)",
                UUID.randomUUID(), Timestamp.valueOf(LocalDateTime.of(2024, 5, 6, 9, 30)), UUID.fromString(ANA),
                "GET /clinical-records/" + ANA);

        mockMvc.perform(get("/record-refusals").param("user", "recepcion")
                        .param("from", "2024-05-06").param("to", "2024-05-06"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Rechazos<")))
                .andExpect(content().string(containsString("lun 06/05/2024 09:30")))
                .andExpect(content().string(containsString("recepcion")))
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(containsString("Ruta GET /clinical-records/" + ANA)));
    }

    @Test
    @WithUserDetails("admin")
    void theFirstDayAfterTheLastIsSaidInSpanish() throws Exception {
        mockMvc.perform(get("/record-refusals").param("from", "2026-03-10").param("to", "2026-03-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El primer día es posterior al último.")));
    }

    @Test
    @WithUserDetails("recepcion")
    void receptionMayNotOpenIt() throws Exception {
        mockMvc.perform(get("/record-refusals")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerMayNotOpenIt() throws Exception {
        mockMvc.perform(get("/record-refusals")).andExpect(status().isForbidden());
    }
}
