package com.clinic.schedule.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.portal.domain.ActivationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Patients and their web accounts for the portal's tests, through the public endpoints, as reception would
 * (D-05). Every patient gets a DNI of their own, out of the development data's range, and every account an
 * address of its own; the emails are read from {@link KeptAccountMail}.
 */
public final class PatientAccountFixture {

    public static final RequestPostProcessor RECEPTION = user("recepcion").roles("RECEPTION");
    public static final String PASSWORD = "una-clave-larga";

    private final MockMvc mockMvc;
    private final ObjectMapper json;

    public PatientAccountFixture(MockMvc mockMvc, ObjectMapper json) {
        this.mockMvc = mockMvc;
        this.json = json;
    }

    /** A patient, and the number of their DNI. */
    public record Patient(String id, String dni) {
    }

    /** "Ana Pérez", with a DNI; {@code birthDate} as {@code 1990-05-14}, or null for none. */
    public Patient patient(boolean seen, String birthDate) throws Exception {
        return patient("Ana Pérez", seen, birthDate);
    }

    /** A patient of that name, with a DNI; {@code birthDate} as {@code 1990-05-14}, or null for none. */
    public Patient patient(String fullName, boolean seen, String birthDate) throws Exception {
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999));
        String body = """
                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%s", "documentSeen": %s,
                 "birthDate": %s}
                """.formatted(fullName, dni, seen, birthDate == null ? "null" : "\"" + birthDate + "\"");
        return new Patient(read(mockMvc.perform(post("/api/patients").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText(), dni);
    }

    /** A verified adult, who may have an account. */
    public Patient adult() throws Exception {
        return patient(true, "1990-05-14");
    }

    public static String address() {
        return "ana-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";
    }

    public JsonNode open(Patient patient, String email) throws Exception {
        return read(mockMvc.perform(post("/api/patient-accounts").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId": "%s", "email": "%s"}
                                """.formatted(patient.id(), email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    public String accountOf(Patient patient) throws Exception {
        return read(mockMvc.perform(get("/api/patient-accounts").with(RECEPTION).param("patientId", patient.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    /** The token of the last activation sent to that address. */
    public static String lastTokenTo(String address) {
        List<ActivationMessage> sent = KeptAccountMail.SENT.stream()
                .filter(message -> message.address().equals(address))
                .toList();
        assertThat(sent).isNotEmpty();
        return sent.get(sent.size() - 1).token();
    }

    public void activate(String token) throws Exception {
        mockMvc.perform(post("/api/portal/activation").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "%s", "password": "%s"}
                                """.formatted(token, PASSWORD)))
                .andExpect(status().isNoContent());
    }

    /** A verified adult with an account in use, whose password is {@link #PASSWORD}. */
    public Patient withAccount() throws Exception {
        return withAccount("Ana Pérez", "1990-05-14");
    }

    /** A verified patient of that name and date of birth, 13 or older, with an account in use. */
    public Patient withAccount(String fullName, String birthDate) throws Exception {
        Patient patient = patient(fullName, true, birthDate);
        String address = address();
        open(patient, address);
        activate(lastTokenTo(address));
        return patient;
    }

    private JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }
}
