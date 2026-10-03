package com.clinic.schedule.portal.adapter.in.http;

import static com.clinic.schedule.portal.PatientAccountFixture.RECEPTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reception forms family groups over the real stack (D-84): who joins by themselves, a child at a parent's
 * request, who shares their appointments, and the group's history. "Today" is 10 March 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FamilyGroupIT {

    private static final RequestPostProcessor PRACTITIONER = user("arias").roles("PRACTITIONER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private String patient(String fullName, String birthDate) throws Exception {
        String body = """
                {"fullName": "%s", "documentType": "DNI", "documentNumber": "%d", "documentSeen": true,
                 "birthDate": %s}
                """.formatted(fullName, ThreadLocalRandom.current().nextInt(50_000_000, 99_999_999),
                birthDate == null ? "null" : "\"" + birthDate + "\"");
        return read(mockMvc.perform(post("/api/patients").with(RECEPTION)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions send(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(RECEPTION).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String group(String name) throws Exception {
        return read(send("/api/family-groups", "{\"name\": \"" + name + "\"}").andExpect(status().isCreated()))
                .get("id").asText();
    }

    private JsonNode join(String group, String patient, boolean shares) throws Exception {
        return read(send("/api/family-groups/" + group + "/members",
                "{\"patientId\": \"" + patient + "\", \"shares\": " + shares + "}").andExpect(status().isOk()));
    }

    private ResultActions addChild(String group, String child, String requester, String relation,
            boolean proofSeen) throws Exception {
        return send("/api/family-groups/" + group + "/children", """
                {"patientId": %s, "requestedBy": %s, "relation": "%s", "proofSeen": %s}
                """.formatted(quoted(child), quoted(requester), relation, proofSeen));
    }

    private static String quoted(String id) {
        return id == null ? "null" : "\"" + id + "\"";
    }

    private static JsonNode member(JsonNode group, String patient) {
        for (JsonNode member : group.get("members")) {
            if (member.get("patientId").asText().equals(patient)) {
                return member;
            }
        }
        throw new AssertionError("not a member: " + patient);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void receptionFormsAFamilyAndReadsWhoSharesTheirAppointments() throws Exception {
        String mother = patient("Marta Gómez", "1985-02-01");
        String son = patient("Tomás Gómez", "2011-01-20");
        String daughter = patient("Sofía Gómez", "2018-07-03");
        String group = group("  Familia Gómez ");

        join(group, mother, false);
        JsonNode joined = join(group, son, false);
        assertThat(member(joined, mother).get("shares").asBoolean()).isTrue();
        assertThat(member(joined, son).get("shares").asBoolean()).isFalse();
        assertThat(member(joined, son).get("fullName").asText()).isEqualTo("Tomás Gómez");

        JsonNode withChild = read(addChild(group, daughter, mother, "MOTHER", true).andExpect(status().isOk()));
        JsonNode child = member(withChild, daughter);
        assertThat(child.get("shares").asBoolean()).isTrue();
        assertThat(child.get("requestedBy").asText()).isEqualTo(mother);
        assertThat(child.get("relation").asText()).isEqualTo("MOTHER");
        assertThat(member(withChild, mother).get("requestedBy").isNull()).isTrue();

        JsonNode sharing = read(send("/api/family-groups/" + group + "/members/" + son + "/sharing",
                "{\"shares\": true}").andExpect(status().isOk()));
        assertThat(member(sharing, son).get("shares").asBoolean()).isTrue();

        JsonNode read = read(mockMvc.perform(get("/api/family-groups/" + group).with(RECEPTION))
                .andExpect(status().isOk()));
        assertThat(read.get("name").asText()).isEqualTo("Familia Gómez");
        assertThat(read.get("members")).hasSize(3);
        JsonNode mine = read(mockMvc.perform(get("/api/family-groups").with(RECEPTION).param("patientId", son))
                .andExpect(status().isOk()));
        assertThat(mine.findValuesAsText("id")).containsExactly(group);
        JsonNode all = read(mockMvc.perform(get("/api/family-groups").with(RECEPTION)).andExpect(status().isOk()));
        assertThat(all.findValuesAsText("id")).contains(group);

        read(send("/api/family-groups/" + group + "/members/" + mother + "/remove", "").andExpect(status().isOk()));
        JsonNode changes = read(mockMvc.perform(get("/api/family-groups/" + group + "/changes").with(RECEPTION))
                .andExpect(status().isOk()));
        // The tests' clock stands still, so every entry has the same time and no order to check.
        assertThat(changes.findValuesAsText("action")).containsExactlyInAnyOrder("REMOVED", "SHARING_STARTED",
                "CHILD_ADDED", "JOINED", "JOINED", "CREATED");
        assertThat(changes.findValuesAsText("by")).containsOnly("recepcion");
        assertThat(changes).allSatisfy(change -> {
            switch (change.get("action").asText()) {
                case "CREATED" -> assertThat(change.get("patientId").isNull()).isTrue();
                case "REMOVED" -> assertThat(change.get("patientId").asText()).isEqualTo(mother);
                case "SHARING_STARTED" -> assertThat(change.get("patientId").asText()).isEqualTo(son);
                case "CHILD_ADDED" -> assertThat(change.get("patientId").asText()).isEqualTo(daughter);
                default -> assertThat(change.get("patientId").asText()).isIn(mother, son);
            }
        });
        jdbc.update("update family_group_change set changed_at = ? where group_id = ? and action = 'CREATED'",
                LocalDateTime.of(2026, 3, 1, 9, 0), UUID.fromString(group));
        jdbc.update("update family_group_change set changed_at = ? where group_id = ? and action = 'REMOVED'",
                LocalDateTime.of(2026, 3, 10, 9, 0), UUID.fromString(group));
        JsonNode ordered = read(mockMvc.perform(get("/api/family-groups/" + group + "/changes").with(RECEPTION)));
        assertThat(ordered.get(0).get("action").asText()).isEqualTo("REMOVED");
        assertThat(ordered.get(5).get("action").asText()).isEqualTo("CREATED");
        JsonNode left = read(mockMvc.perform(get("/api/family-groups/" + group).with(RECEPTION)));
        assertThat(left.get("members").findValuesAsText("patientId")).containsExactlyInAnyOrder(son, daughter);
    }

    @Test
    void theGroupsRulesAreAConflict() throws Exception {
        String father = patient("Luis Ríos", "1980-03-10");
        String teen = patient("Ana Ríos", "2012-03-10");
        String child = patient("Juan Ríos", "2013-03-11");
        String unborn = patient("Sin Fecha", null);
        String group = group("Familia Ríos");

        send("/api/family-groups/" + group + "/members", "{\"patientId\": \"" + child + "\"}")
                .andExpect(status().isConflict());
        send("/api/family-groups/" + group + "/members", "{\"patientId\": \"" + unborn + "\"}")
                .andExpect(status().isConflict());
        join(group, father, false);
        send("/api/family-groups/" + group + "/members", "{\"patientId\": \"" + father + "\"}")
                .andExpect(status().isConflict());
        addChild(group, teen, father, "FATHER", true).andExpect(status().isConflict());
        addChild(group, child, father, "FATHER", false).andExpect(status().isConflict());
        addChild(group, child, teen, "FATHER", true).andExpect(status().isConflict());
        addChild(group, child, father, "FATHER", true).andExpect(status().isOk());
        send("/api/family-groups/" + group + "/members/" + child + "/sharing", "{\"shares\": false}")
                .andExpect(status().isConflict());
        send("/api/family-groups/" + group + "/members/" + teen + "/remove", "").andExpect(status().isConflict());
    }

    @Test
    void whatCannotBeAGroupOrAPatientIsMalformedAndAGroupThatIsNotThereIsNotFound() throws Exception {
        send("/api/family-groups", "{\"name\": \" \"}").andExpect(status().isBadRequest());
        send("/api/family-groups", "{\"name\": \"" + "x".repeat(101) + "\"}").andExpect(status().isBadRequest());
        String group = group("Familia Paz");
        send("/api/family-groups/" + group + "/members", "{\"patientId\": \"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest());
        send("/api/family-groups/" + group + "/children", "{\"patientId\": \"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest());
        send("/api/family-groups/" + group + "/members", "{}").andExpect(status().isBadRequest());
        String child = patient("Teo Paz", "2020-01-01");
        String mother = patient("Rosa Paz", "1990-01-01");
        join(group, mother, false);
        // Without the child, or who asks for them, the request is malformed; without the relation, refused.
        addChild(group, child, null, "MOTHER", true).andExpect(status().isBadRequest());
        addChild(group, null, mother, "MOTHER", true).andExpect(status().isBadRequest());
        send("/api/family-groups/" + group + "/children",
                "{\"patientId\": \"" + child + "\", \"requestedBy\": \"" + mother + "\", \"proofSeen\": true}")
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/family-groups/" + UUID.randomUUID()).with(RECEPTION))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/family-groups/" + UUID.randomUUID() + "/changes").with(RECEPTION))
                .andExpect(status().isNotFound());
        send("/api/family-groups/" + UUID.randomUUID() + "/members",
                "{\"patientId\": \"" + patient("Eva Paz", "1990-01-01") + "\"}").andExpect(status().isNotFound());
    }

    @Test
    void theGroupsAreListedByName() throws Exception {
        String grandmother = patient("Elsa Mora", "1950-05-05");
        String later = group("Mora, la de los nietos");
        String sooner = group("Mora, la de los hijos");
        join(later, grandmother, false);
        join(sooner, grandmother, false);

        JsonNode hers = read(mockMvc.perform(get("/api/family-groups").with(RECEPTION)
                .param("patientId", grandmother)));
        assertThat(hers.findValuesAsText("id")).containsExactly(sooner, later);
        JsonNode all = read(mockMvc.perform(get("/api/family-groups").with(RECEPTION)));
        assertThat(all.findValuesAsText("id")).containsSubsequence(sooner, later);
    }

    @Test
    void theGroupsAreReceptions() throws Exception {
        mockMvc.perform(get("/api/family-groups").with(PRACTITIONER)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/family-groups").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/family-groups").with(PRACTITIONER)).andExpect(status().isForbidden());
    }
}
