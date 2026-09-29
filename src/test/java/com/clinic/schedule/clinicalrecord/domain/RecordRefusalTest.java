package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** What a refused attempt to reach clinical content keeps (D-72). */
class RecordRefusalTest {

    private static final PractitionerId PRACTITIONER = new PractitionerId(UUID.randomUUID());
    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);

    @Test
    void aRefusedRecordIsSomePatientsAndSaysWhoAndWhen() {
        RecordRefusal refusal = RecordRefusal.ofRecord("benitez", PRACTITIONER, PATIENT, NOW);

        assertThat(refusal.attempt()).isEqualTo(RefusedAttempt.RECORD);
        assertThat(refusal.refusedBy()).isEqualTo("benitez");
        assertThat(refusal.practitionerId()).contains(PRACTITIONER);
        assertThat(refusal.patientId()).contains(PATIENT);
        assertThat(refusal.refusedAt()).isEqualTo(NOW);
        assertThat(refusal.route()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> RecordRefusal.ofRecord("benitez", PRACTITIONER, null, NOW));
    }

    /** A file that does not exist is refused too, and whose it would be is unknown. */
    @Test
    void aRefusedFileNamesTheFileAndPerhapsNoPatient() {
        AttachmentId file = AttachmentId.newId();

        RecordRefusal refusal = RecordRefusal.ofAttachment("benitez", PRACTITIONER, file, null, NOW);

        assertThat(refusal.attempt()).isEqualTo(RefusedAttempt.ATTACHMENT);
        assertThat(refusal.attachmentId()).contains(file);
        assertThat(refusal.patientId()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(
                () -> RecordRefusal.ofAttachment("benitez", PRACTITIONER, null, PATIENT, NOW));
    }

    @Test
    void aRefusedRouteKeepsItAndMayNameNoPractitionerNorPatient() {
        RecordRefusal refusal = RecordRefusal.ofRoute("recepcion", null, "GET /api/consultations", null, NOW);

        assertThat(refusal.attempt()).isEqualTo(RefusedAttempt.ROUTE);
        assertThat(refusal.route()).contains("GET /api/consultations");
        assertThat(refusal.practitionerId()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> RecordRefusal.ofRoute("recepcion", null, " ", null, NOW));
    }

    @Test
    void aRouteLongerThanItsColumnIsCutThere() {
        String route = "GET /clinical-records/" + "x".repeat(400);

        assertThat(RecordRefusal.ofRoute("recepcion", null, route, null, NOW).route().orElseThrow())
                .hasSize(RecordRefusal.MAX_ROUTE_LENGTH);
    }

    @Test
    void aRefusalNeedsWhoAndWhen() {
        assertThatIllegalArgumentException().isThrownBy(() -> RecordRefusal.ofRecord(" ", PRACTITIONER, PATIENT, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> RecordRefusal.ofRecord("benitez", PRACTITIONER, PATIENT, null));
    }
}
