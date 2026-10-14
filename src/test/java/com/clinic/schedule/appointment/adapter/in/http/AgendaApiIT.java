package com.clinic.schedule.appointment.adapter.in.http;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.clinic.schedule.appointment.PatientFixture;
import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.arrival.ArrivalFixture;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reception's agenda of a day for its application (D-98), over the real stack: the appointments not cancelled,
 * in order, with the names of their patients and practitioners, who arrived and who warned. Each test books
 * with practitioners of its own, on the tests' "today", Tuesday 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "recepcion", roles = "RECEPTION")
class AgendaApiIT {

    private static final String DAY = "2026-03-10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private PractitionerFixture practitioners;
    private PatientFixture patients;

    @BeforeEach
    void setUp() {
        practitioners = new PractitionerFixture(mockMvc, json);
        patients = new PatientFixture(mockMvc, json);
    }

    private String book(CreatedPractitioner practitioner, UUID patient, String time) throws Exception {
        return json.readTree(mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%sT%s:00"}
                                """.formatted(patient, practitioner.id(), practitioner.consulta(), DAY, time)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private UUID patientWithADocument(String fullName, String dni) throws Exception {
        return UUID.fromString(json.readTree(mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%s"}
                                """.formatted(fullName, dni)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText());
    }

    private String nameOf(CreatedPractitioner practitioner) {
        return jdbc.queryForObject("select full_name from practitioner where id = ?", String.class, practitioner.id());
    }

    @Test
    void aPractitionersDayComesInOrderWithItsNamesWhoArrivedAndWhoWarned() throws Exception {
        CreatedPractitioner practitioner = practitioners.workingEveryDay();
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999));
        UUID withDocument = patientWithADocument("Rosa Agenda", dni);
        String late = book(practitioner, patients.newPatient(), "11:00");
        String arrived = book(practitioner, withDocument, "10:00");
        String cancelled = book(practitioner, patients.newPatient(), "12:00");
        new ArrivalFixture(mockMvc, jdbc).arrived(arrived);
        mockMvc.perform(post("/api/appointments/" + late + "/late-arrival-notice")).andExpect(status().isOk());
        mockMvc.perform(post("/api/appointments/" + cancelled + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestedBy\": \"PATIENT\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/api/agenda").param("practitionerId", practitioner.id().toString()).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DAY))
                .andExpect(jsonPath("$.practitionerId").value(practitioner.id().toString()))
                .andExpect(jsonPath("$.practitioners[*].fullName", hasItem(nameOf(practitioner))))
                .andExpect(jsonPath("$.appointments.length()").value(2))
                .andExpect(jsonPath("$.appointments[0].id").value(arrived))
                .andExpect(jsonPath("$.appointments[0].startsAt").value(DAY + "T10:00:00"))
                .andExpect(jsonPath("$.appointments[0].durationMinutes").value(30))
                .andExpect(jsonPath("$.appointments[0].patient").value("Rosa Agenda"))
                .andExpect(jsonPath("$.appointments[0].patientDocument").value("DNI " + dni))
                .andExpect(jsonPath("$.appointments[0].practitioner").value(nameOf(practitioner)))
                .andExpect(jsonPath("$.appointments[0].reason").value("consulta"))
                .andExpect(jsonPath("$.appointments[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.appointments[0].arrivedAt").isNotEmpty())
                .andExpect(jsonPath("$.appointments[0].bookedVia").value("RECEPTION"))
                .andExpect(jsonPath("$.appointments[1].id").value(late))
                .andExpect(jsonPath("$.appointments[1].patientDocument").isEmpty())
                .andExpect(jsonPath("$.appointments[1].arrivedAt").isEmpty())
                .andExpect(jsonPath("$.appointments[1].lateArrivalNotice").value(true))
                .andExpect(jsonPath("$.appointments[1].movedOnWeb").value(false));
    }

    /** Without a practitioner, everyone's; without a day, today's by the practice's clock, never the browser's. */
    @Test
    void withoutAPractitionerItIsEveryonesAndWithoutADayItIsToday() throws Exception {
        CreatedPractitioner one = practitioners.workingEveryDay();
        CreatedPractitioner other = practitioners.workingEveryDay();
        String first = book(one, patients.newPatient(), "09:00");
        String second = book(other, patients.newPatient(), "09:00");

        mockMvc.perform(get("/api/agenda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(DAY))
                .andExpect(jsonPath("$.practitionerId").isEmpty())
                .andExpect(jsonPath("$.appointments[*].id", hasItems(first, second)));

        mockMvc.perform(get("/api/agenda").param("practitionerId", other.id().toString()))
                .andExpect(jsonPath("$.appointments[*].practitionerId", everyItem(is(other.id().toString()))))
                .andExpect(jsonPath("$.appointments[*].id", not(hasItem(first))));
    }

    /** Reception's alone for now (D-34): the practitioners' application reads it when it is built. */
    @Test
    void onlyReceptionReadsIt() throws Exception {
        mockMvc.perform(get("/api/agenda").with(httpBasic("arias", "arias123"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda").with(httpBasic("admin", "admin123"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda").with(httpBasic("sala", "sala123"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/agenda").with(anonymous())).andExpect(status().isUnauthorized());
    }

    @Test
    void aDayThatIsNoneIsA400() throws Exception {
        mockMvc.perform(get("/api/agenda").param("date", "mañana")).andExpect(status().isBadRequest());
    }
}
