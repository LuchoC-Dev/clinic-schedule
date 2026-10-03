package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.PASSWORD;
import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinic.schedule.appointment.PractitionerFixture;
import com.clinic.schedule.appointment.PractitionerFixture.CreatedPractitioner;
import com.clinic.schedule.portal.KeptAccountMail;
import com.clinic.schedule.portal.PatientAccountFixture;
import com.clinic.schedule.portal.PatientAccountFixture.Patient;
import com.clinic.schedule.portal.domain.BookingConfirmation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The family on the web, over the real stack (D-85): an adult sees, books and cancels the appointments of the
 * members who share theirs; a member under 18 acts only for themselves; from 13 each one shares, stops or leaves.
 * "Today" is 10 March 2026, at midnight; the practitioner works every day and books "consulta" on the web.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(KeptAccountMail.class)
class FamilyWebIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private PatientAccountFixture accounts;
    private CreatedPractitioner arias;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new PatientAccountFixture(mockMvc, json);
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("recepcion",
                null, List.of(new SimpleGrantedAuthority("ROLE_RECEPTION"))));
        try {
            arias = new PractitionerFixture(mockMvc, json).workingEveryDay();
        } finally {
            TestSecurityContextHolder.clearContext();
        }
        mockMvc.perform(put("/api/practitioners/" + arias.id() + "/reasons/" + arias.consulta()).with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"consulta\", \"durationMinutes\": 30, \"webBookable\": true}"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor as(Patient patient) {
        return httpBasic("DNI " + patient.dni(), PASSWORD);
    }

    private ResultActions send(RequestPostProcessor who, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String group(String name) throws Exception {
        return read(send(RECEPTION, "/api/family-groups", "{\"name\": \"" + name + "\"}")
                .andExpect(status().isCreated())).get("id").asText();
    }

    private void join(String group, Patient patient, boolean shares) throws Exception {
        send(RECEPTION, "/api/family-groups/" + group + "/members",
                "{\"patientId\": \"" + patient.id() + "\", \"shares\": " + shares + "}").andExpect(status().isOk());
    }

    private void addChild(String group, Patient child, Patient parent) throws Exception {
        send(RECEPTION, "/api/family-groups/" + group + "/children", """
                {"patientId": "%s", "requestedBy": "%s", "relation": "MOTHER", "proofSeen": true}
                """.formatted(child.id(), parent.id())).andExpect(status().isOk());
    }

    /** Reception books it, as anyone's appointment. */
    private String bookAtReception(Patient patient, String startsAt) throws Exception {
        return read(send(RECEPTION, "/api/appointments", """
                {"patientId": "%s", "practitionerId": "%s", "reasonId": "%s", "startsAt": "%s"}
                """.formatted(patient.id(), arias.id(), arias.consulta(), startsAt)).andExpect(status().isCreated()))
                .get("id").asText();
    }

    private ResultActions bookOnWeb(Patient booker, Patient forWhom, String startsAt) throws Exception {
        return send(as(booker), "/api/portal/appointments", """
                {"practitionerId": "%s", "reasonId": "%s", "startsAt": "%s", "patientId": %s}
                """.formatted(arias.id(), arias.consulta(), startsAt,
                forWhom == null ? "null" : "\"" + forWhom.id() + "\""));
    }

    private JsonNode family(Patient patient) throws Exception {
        return read(mockMvc.perform(get("/api/portal/appointments").with(as(patient))).andExpect(status().isOk()))
                .get("family");
    }

    /** The members' names, and not their practitioners'. */
    private static List<String> names(JsonNode family) {
        List<String> names = new ArrayList<>();
        family.forEach(member -> names.add(member.get("fullName").asText()));
        return names;
    }

    @Test
    void anAdultSeesBooksAndCancelsTheAppointmentsOfTheMembersWhoShare() throws Exception {
        Patient mother = accounts.withAccount("Marta Díaz", "1985-04-02");
        Patient partner = accounts.patient("Raúl Díaz", true, "1983-01-15");
        Patient privateTeen = accounts.patient("Tomás Díaz", true, "2011-06-20");
        Patient child = accounts.patient("Sofía Díaz", true, "2018-09-01");
        Patient stranger = accounts.patient("Eva Díaz", true, "1990-02-02");
        String group = group("Familia Díaz");
        join(group, mother, true);
        join(group, partner, true);
        join(group, privateTeen, false);
        addChild(group, child, mother);
        String childs = bookAtReception(child, "2026-03-12T09:00:00");
        String teens = bookAtReception(privateTeen, "2026-03-12T11:00:00");

        JsonNode family = family(mother);
        assertThat(names(family)).containsExactly("Raúl Díaz", "Sofía Díaz");
        JsonNode sofia = family.get(1);
        assertThat(sofia.get("patientId").asText()).isEqualTo(child.id());
        assertThat(sofia.get("upcoming").get(0).get("id").asText()).isEqualTo(childs);
        assertThat(sofia.get("upcoming").get(0).get("reason").asText()).isEqualTo("consulta");
        assertThat(sofia.get("past")).isEmpty();
        JsonNode options = read(mockMvc.perform(get("/api/portal/booking/options").with(as(mother))));
        assertThat(options.get("patients").findValuesAsText("fullName"))
                .containsExactly("Marta Díaz", "Raúl Díaz", "Sofía Díaz");

        JsonNode booked = read(bookOnWeb(mother, child, "2026-03-13T10:00:00").andExpect(status().isCreated()));
        assertThat(booked.get("patientId").asText()).isEqualTo(child.id());
        BookingConfirmation confirmation = KeptAccountMail.CONFIRMATIONS.stream()
                .filter(sent -> "Sofía Díaz".equals(sent.member()))
                .findFirst().orElseThrow();
        assertThat(confirmation.fullName()).isEqualTo("Marta Díaz");
        assertThat(family(mother).get(1).get("upcoming").findValuesAsText("startsAt"))
                .containsExactly("2026-03-12T09:00:00", "2026-03-13T10:00:00");
        assertThat(read(bookOnWeb(mother, mother, "2026-03-13T11:00:00").andExpect(status().isCreated()))
                .get("patientId").asText()).isEqualTo(mother.id());

        bookOnWeb(mother, privateTeen, "2026-03-14T10:00:00").andExpect(status().isForbidden());
        bookOnWeb(mother, stranger, "2026-03-14T10:00:00").andExpect(status().isForbidden());
        send(as(mother), "/api/portal/appointments/" + teens + "/cancel", "").andExpect(status().isForbidden());
        send(as(mother), "/api/portal/appointments/" + childs + "/cancel", "").andExpect(status().isNoContent());
        assertThat(family(mother).get(1).get("upcoming").get(0).get("state").asText()).isEqualTo("CANCELLED");
        assertThat(family(mother).get(1).get("upcoming").get(0).get("cancellationRequestedBy").asText())
                .isEqualTo("PATIENT");

        String missed = bookAtReception(child, "2026-03-20T09:00:00");
        jdbc.update("update appointment set status = 'ABSENT', starts_at = cast('2026-03-02 09:00:00' as timestamp) "
                + "where id = ?", UUID.fromString(missed));
        assertThat(family(mother).get(1).get("past").get(0).get("state").asText()).isEqualTo("ABSENT");
        // A date of birth taken away later: an adult who joined shares as they chose.
        mockMvc.perform(put("/api/patients/" + partner.id() + "/details").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(names(family(mother))).containsExactly("Raúl Díaz", "Sofía Díaz");
    }

    @Test
    void threeAheadAreCountedForWhomTheyAre() throws Exception {
        Patient mother = accounts.withAccount("Clara Ruiz", "1980-01-01");
        Patient child = accounts.patient("Lucas Ruiz", true, "2017-01-01");
        String group = group("Familia Ruiz");
        join(group, mother, true);
        addChild(group, child, mother);
        bookOnWeb(mother, child, "2026-03-11T08:00:00").andExpect(status().isCreated());
        bookOnWeb(mother, child, "2026-03-12T08:00:00").andExpect(status().isCreated());
        bookAtReception(child, "2026-03-13T08:00:00");

        bookOnWeb(mother, child, "2026-03-14T08:00:00").andExpect(status().isConflict());
        bookOnWeb(mother, null, "2026-03-14T08:00:00").andExpect(status().isCreated());
        bookOnWeb(mother, child, "2026-03-15T08:00:00").andExpect(status().isConflict());
    }

    @Test
    void underEighteenAPatientActsOnlyForThemselves() throws Exception {
        Patient teen = accounts.withAccount("Iara Sosa", "2010-05-05");
        Patient mother = accounts.patient("Nadia Sosa", true, "1978-08-08");
        Patient child = accounts.patient("Joaquín Sosa", true, "2019-03-03");
        String group = group("Familia Sosa");
        join(group, mother, true);
        join(group, teen, true);
        addChild(group, child, mother);

        assertThat(family(teen)).isEmpty();
        JsonNode options = read(mockMvc.perform(get("/api/portal/booking/options").with(as(teen))));
        assertThat(options.get("patients").findValuesAsText("fullName")).containsExactly("Iara Sosa");
        bookOnWeb(teen, child, "2026-03-14T10:00:00").andExpect(status().isForbidden());
        bookOnWeb(teen, mother, "2026-03-14T10:00:00").andExpect(status().isForbidden());
        String childs = bookAtReception(child, "2026-03-12T09:00:00");
        send(as(teen), "/api/portal/appointments/" + childs + "/cancel", "").andExpect(status().isForbidden());
    }

    @Test
    void fromThirteenEachOneSharesStopsOrLeavesFromTheWeb() throws Exception {
        Patient teen = accounts.withAccount("Bruno Paz", "2011-11-11");
        Patient father = accounts.withAccount("Hugo Paz", "1975-07-07");
        Patient child = accounts.patient("Alma Paz", true, "2020-02-02");
        String group = group("Familia Paz");
        join(group, father, true);
        join(group, teen, false);
        addChild(group, child, father);

        JsonNode mine = read(mockMvc.perform(get("/api/portal/family").with(as(teen))).andExpect(status().isOk()));
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("name").asText()).isEqualTo("Familia Paz");
        JsonNode members = mine.get(0).get("members");
        // The patient first, then by name: Alma was added after Hugo.
        assertThat(members.findValuesAsText("fullName")).containsExactly("Bruno Paz", "Alma Paz", "Hugo Paz");
        assertThat(members.findValuesAsText("age")).containsExactly("ADOLESCENT", "CHILD", "ADULT");
        assertThat(members.findValuesAsText("shares")).containsExactly("false", "true", "true");
        assertThat(members.findValuesAsText("me")).containsExactly("true", "false", "false");
        assertThat(members.get(1).has("patientId")).isFalse();
        assertThat(names(family(father))).containsExactly("Alma Paz");

        JsonNode shared = read(send(as(teen), "/api/portal/family/" + group + "/sharing", "{\"shares\": true}")
                .andExpect(status().isOk()));
        assertThat(shared.get(0).get("members").get(0).get("shares").asBoolean()).isTrue();
        assertThat(names(family(father))).containsExactly("Alma Paz", "Bruno Paz");
        send(as(teen), "/api/portal/family/" + group + "/sharing", "{\"shares\": false}").andExpect(status().isOk());
        assertThat(names(family(father))).containsExactly("Alma Paz");

        JsonNode left = read(send(as(father), "/api/portal/family/" + group + "/leave", "")
                .andExpect(status().isOk()));
        assertThat(left).isEmpty();
        assertThat(family(father)).isEmpty();
        send(as(father), "/api/portal/family/" + group + "/leave", "").andExpect(status().isConflict());
        send(as(father), "/api/portal/family/" + UUID.randomUUID() + "/leave", "").andExpect(status().isNotFound());
        JsonNode stays = read(mockMvc.perform(get("/api/family-groups/" + group).with(RECEPTION)));
        assertThat(stays.get("members").findValuesAsText("fullName")).containsExactlyInAnyOrder("Bruno Paz",
                "Alma Paz");

        JsonNode changes = read(mockMvc.perform(get("/api/family-groups/" + group + "/changes").with(RECEPTION)));
        assertThat(changes.findValuesAsText("by")).filteredOn(by -> by.startsWith("portal:")).hasSize(3);
    }
}
