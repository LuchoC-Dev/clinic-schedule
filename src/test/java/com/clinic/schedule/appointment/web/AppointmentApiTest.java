package com.clinic.schedule.appointment.web;

import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The API over the real stack: Spring, the database and the schema.
 *
 * <p>Each test invents its own patient and practitioner, so tests cannot collide with each
 * other over a slot and nothing has to be cleaned up in between.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentApiTest {

    private static final String TEN = "2026-03-10T10:00:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private String bookingBody(UUID patientId, UUID practitionerId, String startsAt, int minutes) {
        return """
                {"patientId": "%s", "practitionerId": "%s", "startsAt": "%s",
                 "durationMinutes": %d, "reason": "consulta"}
                """.formatted(patientId, practitionerId, startsAt, minutes);
    }

    private JsonNode bookSuccessfully(UUID patientId, UUID practitionerId, String startsAt, int minutes)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(patientId, practitionerId, startsAt, minutes)))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void bookingAnswers201WithTheAppointmentAndItsLocation() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID practitionerId = UUID.randomUUID();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(patientId, practitionerId, TEN, 30)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.patientId").value(patientId.toString()))
                .andExpect(jsonPath("$.practitionerId").value(practitionerId.toString()))
                .andExpect(jsonPath("$.startsAt").value(TEN))
                .andExpect(jsonPath("$.endsAt").value("2026-03-10T10:30:00"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.reason").value("consulta"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                // An absent note comes back as an explicit null rather than being left out,
                // so a client knows the field exists. Asserted with nullValue() and not with
                // doesNotExist(), which would also pass here because it treats null as absent.
                .andExpect(jsonPath("$.note").value(nullValue()));
    }

    @Test
    void anOverlapAnswers409() throws Exception {
        UUID practitionerId = UUID.randomUUID();
        bookSuccessfully(UUID.randomUUID(), practitionerId, TEN, 30);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(UUID.randomUUID(), practitionerId, "2026-03-10T10:15:00", 30)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void aMissingFieldAnswers400AndNamesIt() throws Exception {
        String withoutPatient = """
                {"practitionerId": "%s", "startsAt": "%s", "durationMinutes": 30, "reason": "consulta"}
                """.formatted(UUID.randomUUID(), TEN);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withoutPatient))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fields.patientId").value("the patient is required"));
    }

    @Test
    void aZeroDurationAnswers400() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(UUID.randomUUID(), UUID.randomUUID(), TEN, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.durationMinutes").exists());
    }

    @Test
    void sendingTheSameIdTwiceBooksOnce() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                {"id": "%s", "patientId": "%s", "practitionerId": "%s", "startsAt": "%s",
                 "durationMinutes": 30, "reason": "consulta"}
                """.formatted(id, UUID.randomUUID(), UUID.randomUUID(), TEN);

        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void reschedulingMovesTheAppointment() throws Exception {
        JsonNode booked = bookSuccessfully(UUID.randomUUID(), UUID.randomUUID(), TEN, 30);

        mockMvc.perform(post("/api/appointments/" + booked.get("id").asText() + "/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startsAt": "2026-03-10T16:00:00", "durationMinutes": 45}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsAt").value("2026-03-10T16:00:00"))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void cancellingKeepsTheAppointmentAsCancelled() throws Exception {
        JsonNode booked = bookSuccessfully(UUID.randomUUID(), UUID.randomUUID(), TEN, 30);

        mockMvc.perform(post("/api/appointments/" + booked.get("id").asText() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingTwiceAnswers409() throws Exception {
        JsonNode booked = bookSuccessfully(UUID.randomUUID(), UUID.randomUUID(), TEN, 30);
        String cancelUrl = "/api/appointments/" + booked.get("id").asText() + "/cancel";

        mockMvc.perform(post(cancelUrl)).andExpect(status().isOk());
        mockMvc.perform(post(cancelUrl)).andExpect(status().isConflict());
    }

    @Test
    void anUnknownAppointmentAnswers404() throws Exception {
        mockMvc.perform(post("/api/appointments/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void theDayAgendaComesBackInOrderAndWithoutCancelledOnes() throws Exception {
        UUID practitionerId = UUID.randomUUID();
        bookSuccessfully(UUID.randomUUID(), practitionerId, "2026-03-10T15:00:00", 30);
        bookSuccessfully(UUID.randomUUID(), practitionerId, "2026-03-10T09:00:00", 30);
        JsonNode toCancel = bookSuccessfully(UUID.randomUUID(), practitionerId, "2026-03-10T12:00:00", 30);
        mockMvc.perform(post("/api/appointments/" + toCancel.get("id").asText() + "/cancel"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/appointments")
                        .param("practitionerId", practitionerId.toString())
                        .param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startsAt").value("2026-03-10T09:00:00"))
                .andExpect(jsonPath("$[1].startsAt").value("2026-03-10T15:00:00"));
    }

    @Test
    void theDayAgendaOfAPractitionerWithNothingBookedIsEmpty() throws Exception {
        mockMvc.perform(get("/api/appointments")
                        .param("practitionerId", UUID.randomUUID().toString())
                        .param("date", "2026-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
