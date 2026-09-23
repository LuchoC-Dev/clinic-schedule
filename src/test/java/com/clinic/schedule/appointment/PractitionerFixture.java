package com.clinic.schedule.appointment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Real practitioners for the appointment tests, created through the practitioner module's
 * public endpoints. The appointment tests stay out of that module's internals the same way
 * the appointment code does (D-05).
 *
 * <p>Every call creates a new practitioner, so tests sharing the database cannot collide over
 * a slot.
 */
public final class PractitionerFixture {

    /** A practitioner and the ids of their two reasons: consulta takes 30', ecografía 45'. */
    public record CreatedPractitioner(UUID id, UUID consulta, UUID ecografia) {
    }

    private final MockMvc mockMvc;
    private final ObjectMapper json;

    public PractitionerFixture(MockMvc mockMvc, ObjectMapper json) {
        this.mockMvc = mockMvc;
        this.json = json;
    }

    /** Works every day from 07:00 to 21:00, for tests about something other than hours. */
    public CreatedPractitioner workingEveryDay() throws Exception {
        String everyDay = String.join(",",
                stretch("MONDAY", "07:00", "21:00"), stretch("TUESDAY", "07:00", "21:00"),
                stretch("WEDNESDAY", "07:00", "21:00"), stretch("THURSDAY", "07:00", "21:00"),
                stretch("FRIDAY", "07:00", "21:00"), stretch("SATURDAY", "07:00", "21:00"),
                stretch("SUNDAY", "07:00", "21:00"));
        return create(everyDay);
    }

    /** Works only on Tuesdays, 09:00 to 13:00 and 14:00 to 18:00: a day with a lunch break. */
    public CreatedPractitioner workingTuesdaysWithALunchBreak() throws Exception {
        return create(stretch("TUESDAY", "09:00", "13:00") + "," + stretch("TUESDAY", "14:00", "18:00"));
    }

    private CreatedPractitioner create(String stretches) throws Exception {
        String id = idOf(mockMvc.perform(post("/api/practitioners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Dra. Arias"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());

        UUID consulta = UUID.fromString(addReason(id, "consulta", 30));
        UUID ecografia = UUID.fromString(addReason(id, "ecografia", 45));

        mockMvc.perform(put("/api/practitioners/" + id + "/weekly-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\": [" + stretches + "]}"))
                .andExpect(status().isOk());

        return new CreatedPractitioner(UUID.fromString(id), consulta, ecografia);
    }

    private String addReason(String practitionerId, String name, int minutes) throws Exception {
        return idOf(mockMvc.perform(post("/api/practitioners/" + practitionerId + "/reasons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "durationMinutes": %d}
                                """.formatted(name, minutes)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static String stretch(String day, String from, String until) {
        return """
                {"day": "%s", "startsAt": "%s", "endsAt": "%s"}""".formatted(day, from, until);
    }

    private String idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
