package com.clinic.schedule.practitioner.presentation.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The practitioner endpoints over the real stack. Each test registers its own practitioner,
 * so they share the database without interfering.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "RECEPTION")
class PractitionerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String registerPractitioner() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/practitioners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Dra. Arias"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asText();
    }

    private String addReason(String practitionerId, String name, int minutes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/practitioners/" + practitionerId + "/reasons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "durationMinutes": %d}
                                """.formatted(name, minutes)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asText();
    }

    @Test
    void registeringAnswers201WithAnEmptyConfiguration() throws Exception {
        mockMvc.perform(post("/api/practitioners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Dr. Benitez"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.fullName").value("Dr. Benitez"))
                .andExpect(jsonPath("$.reasons.length()").value(0))
                .andExpect(jsonPath("$.weeklySchedule.length()").value(0));
    }

    @Test
    void reasonsAreAddedAndComeBackByName() throws Exception {
        String id = registerPractitioner();
        addReason(id, "control", 20);
        addReason(id, "consulta", 30);

        mockMvc.perform(get("/api/practitioners/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasons.length()").value(2))
                .andExpect(jsonPath("$.reasons[0].name").value("consulta"))
                .andExpect(jsonPath("$.reasons[0].durationMinutes").value(30))
                .andExpect(jsonPath("$.reasons[1].name").value("control"));
    }

    @Test
    void aRepeatedReasonNameAnswers409() throws Exception {
        String id = registerPractitioner();
        addReason(id, "consulta", 30);

        mockMvc.perform(post("/api/practitioners/" + id + "/reasons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "CONSULTA", "durationMinutes": 45}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void twoPractitionersMayHaveTheSameReasonWithDifferentDurations() throws Exception {
        String arias = registerPractitioner();
        String benitez = registerPractitioner();

        addReason(arias, "consulta", 30);
        addReason(benitez, "consulta", 45);

        mockMvc.perform(get("/api/practitioners/" + benitez))
                .andExpect(jsonPath("$.reasons[0].durationMinutes").value(45));
    }

    @Test
    void changingAReasonKeepsItsId() throws Exception {
        String id = registerPractitioner();
        String reasonId = addReason(id, "control", 20);

        mockMvc.perform(put("/api/practitioners/" + id + "/reasons/" + reasonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "control anual", "durationMinutes": 40}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reasonId))
                .andExpect(jsonPath("$.name").value("control anual"))
                .andExpect(jsonPath("$.durationMinutes").value(40));
    }

    @Test
    void anUnknownReasonAnswers404() throws Exception {
        String id = registerPractitioner();

        mockMvc.perform(put("/api/practitioners/" + id + "/reasons/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "consulta", "durationMinutes": 30}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void aZeroDurationAnswers400NamingTheField() throws Exception {
        String id = registerPractitioner();

        mockMvc.perform(post("/api/practitioners/" + id + "/reasons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "consulta", "durationMinutes": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.durationMinutes").exists());
    }

    @Test
    void theWeekIsStoredAndReadBackInOrder() throws Exception {
        String id = registerPractitioner();

        mockMvc.perform(put("/api/practitioners/" + id + "/weekly-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hours": [
                                  {"day": "TUESDAY", "startsAt": "09:00", "endsAt": "13:00"},
                                  {"day": "MONDAY",  "startsAt": "14:00", "endsAt": "18:00"},
                                  {"day": "MONDAY",  "startsAt": "09:00", "endsAt": "13:00"}
                                ]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/practitioners/" + id))
                .andExpect(jsonPath("$.weeklySchedule.length()").value(3))
                .andExpect(jsonPath("$.weeklySchedule[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.weeklySchedule[0].startsAt").value("09:00:00"))
                .andExpect(jsonPath("$.weeklySchedule[1].startsAt").value("14:00:00"))
                .andExpect(jsonPath("$.weeklySchedule[2].day").value("TUESDAY"));
    }

    @Test
    void replacingTheWeekDropsTheOldHours() throws Exception {
        String id = registerPractitioner();
        String url = "/api/practitioners/" + id + "/weekly-schedule";
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("""
                {"hours": [{"day": "MONDAY", "startsAt": "09:00", "endsAt": "13:00"}]}
                """)).andExpect(status().isOk());

        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("""
                {"hours": [{"day": "FRIDAY", "startsAt": "10:00", "endsAt": "12:00"}]}
                """)).andExpect(status().isOk());

        mockMvc.perform(get("/api/practitioners/" + id))
                .andExpect(jsonPath("$.weeklySchedule.length()").value(1))
                .andExpect(jsonPath("$.weeklySchedule[0].day").value("FRIDAY"));
    }

    @Test
    void anOverlappingWeekAnswers400AndExplains() throws Exception {
        String id = registerPractitioner();

        mockMvc.perform(put("/api/practitioners/" + id + "/weekly-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hours": [
                                  {"day": "MONDAY", "startsAt": "09:00", "endsAt": "13:00"},
                                  {"day": "MONDAY", "startsAt": "12:00", "endsAt": "18:00"}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void hoursEndingBeforeTheyStartAnswer400() throws Exception {
        String id = registerPractitioner();

        mockMvc.perform(put("/api/practitioners/" + id + "/weekly-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hours": [{"day": "MONDAY", "startsAt": "13:00", "endsAt": "09:00"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStretchWithoutADayAnswers400NamingTheField() throws Exception {
        String id = registerPractitioner();

        mockMvc.perform(put("/api/practitioners/" + id + "/weekly-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hours": [{"startsAt": "09:00", "endsAt": "13:00"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['hours[0].day']").value("the day is required"));
    }

    @Test
    void anUnknownPractitionerAnswers404() throws Exception {
        mockMvc.perform(get("/api/practitioners/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void aReasonLongerThanAWorkingDayAnswers400() throws Exception {
        String id = registerPractitioner();

        mockMvc.perform(post("/api/practitioners/" + id + "/reasons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "eterna", "durationMinutes": 481}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A reason lasts at most 480 minutes"));
    }
}
