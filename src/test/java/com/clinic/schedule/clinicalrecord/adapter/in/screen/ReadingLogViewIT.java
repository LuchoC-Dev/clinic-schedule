package com.clinic.schedule.clinicalrecord.adapter.in.screen;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

/** The admins' screen of who read which clinical record (D-71). */
@SpringBootTest
@AutoConfigureMockMvc
class ReadingLogViewIT {

    /** Ana Pérez, of the development data, read by Dra. Arias, also of the development data. */
    private static final String ANA = "c3333333-0000-0000-0000-000000000001";
    private static final String ARIAS = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithUserDetails("admin")
    void theAdminSeesWhoReadWhoseRecordInSpanish() throws Exception {
        jdbc.update("insert into record_reading (id, practitioner_id, read_by, patient_id, read_at, access, attachment_id) "
                        + "values (?, ?, null, ?, ?, 'ACCESS_GRANT', null)",
                UUID.randomUUID(), UUID.fromString(ARIAS), UUID.fromString(ANA),
                Timestamp.valueOf(LocalDateTime.of(2024, 5, 6, 9, 30)));

        mockMvc.perform(get("/record-readings").param("patient", "Ana").param("from", "2024-05-06").param("to", "2024-05-06"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Lecturas<")))
                .andExpect(content().string(not(containsString(">Agenda<"))))
                .andExpect(content().string(containsString("lun 06/05/2024 09:30")))
                .andExpect(content().string(containsString("Dra. Laura Arias")))
                .andExpect(content().string(containsString("sin usuario registrado")))
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(containsString("Acceso pedido")))
                .andExpect(content().string(containsString(">Historia<")));
    }

    @Test
    @WithUserDetails("admin")
    void theFirstDayAfterTheLastIsSaidInSpanish() throws Exception {
        mockMvc.perform(get("/record-readings").param("from", "2026-03-10").param("to", "2026-03-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El primer día es posterior al último.")));
    }

    @Test
    @WithUserDetails("recepcion")
    void receptionMayNotOpenIt() throws Exception {
        mockMvc.perform(get("/record-readings")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("arias")
    void aPractitionerMayNotOpenIt() throws Exception {
        mockMvc.perform(get("/record-readings")).andExpect(status().isForbidden());
    }
}
