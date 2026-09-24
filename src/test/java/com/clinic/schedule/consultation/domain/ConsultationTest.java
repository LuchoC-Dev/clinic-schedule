package com.clinic.schedule.consultation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConsultationTest {

    private static final ConsultationId ID = ConsultationId.newId();
    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final PractitionerId PRACTITIONER = new PractitionerId(UUID.randomUUID());
    private static final LocalDateTime AT = LocalDateTime.of(2026, 3, 10, 10, 15);

    private static Consultation withNotes(String notes, List<ConsultationItem> items) {
        return Consultation.record(ID, PATIENT, PRACTITIONER, AT, notes, items);
    }

    @Test
    void aConsultationKeepsWhatWasWrittenInOrder() {
        List<ConsultationItem> items = List.of(
                new ConsultationItem(ItemKind.CHECK, "  presión 120/80  "),
                new ConsultationItem(ItemKind.PRESCRIPTION, "enalapril 10 mg"),
                new ConsultationItem(ItemKind.STUDY_ORDER, "ecodoppler de vasos de cuello"));

        Consultation consultation = withNotes("  Control de hipertensión.  ", items);

        assertThat(consultation.notes()).isEqualTo("Control de hipertensión.");
        assertThat(consultation.items()).extracting(ConsultationItem::kind)
                .containsExactly(ItemKind.CHECK, ItemKind.PRESCRIPTION, ItemKind.STUDY_ORDER);
        assertThat(consultation.items().getFirst().description()).isEqualTo("presión 120/80");
        assertThat(consultation.recordedAt()).isEqualTo(AT);
    }

    @Test
    void itemsAreOptional() {
        assertThat(withNotes("Consulta por tos.", List.of()).items()).isEmpty();
    }

    @Test
    void whatWasConsultedIsRequired() {
        assertThatIllegalArgumentException().isThrownBy(() -> withNotes(null, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> withNotes("   ", List.of()));
    }

    @Test
    void itNeedsAnIdAPatientAPractitionerAndATime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Consultation.record(null, PATIENT, PRACTITIONER, AT, "notas", List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Consultation.record(ID, null, PRACTITIONER, AT, "notas", List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Consultation.record(ID, PATIENT, null, AT, "notas", List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Consultation.record(ID, PATIENT, PRACTITIONER, null, "notas", List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Consultation.record(ID, PATIENT, PRACTITIONER, AT, "notas", null));
    }

    @Test
    void theNotesAreAtMostTenThousandCharacters() {
        assertThat(withNotes("a".repeat(10_000), List.of()).notes()).hasSize(10_000);
        assertThatIllegalArgumentException().isThrownBy(() -> withNotes("a".repeat(10_001), List.of()));
    }

    @Test
    void aConsultationHasAtMostFiftyItems() {
        ConsultationItem item = new ConsultationItem(ItemKind.CHECK, "pulso 72");

        assertThat(withNotes("notas", Collections.nCopies(50, item)).items()).hasSize(50);
        assertThatIllegalArgumentException().isThrownBy(() -> withNotes("notas", Collections.nCopies(51, item)));
    }

    @Test
    void anItemNeedsAKindAndADescriptionOfAtMostTwoThousandCharacters() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConsultationItem(null, "pulso 72"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ConsultationItem(ItemKind.CHECK, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new ConsultationItem(ItemKind.CHECK, null));
        assertThat(new ConsultationItem(ItemKind.CHECK, "a".repeat(2_000)).description()).hasSize(2_000);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConsultationItem(ItemKind.CHECK, "a".repeat(2_001)));
    }

    @Test
    void whatWasSentCannotBeChangedThroughTheListItCameIn() {
        List<ConsultationItem> items = new ArrayList<>(List.of(new ConsultationItem(ItemKind.CHECK, "pulso 72")));
        Consultation consultation = withNotes("notas", items);

        items.add(new ConsultationItem(ItemKind.PRESCRIPTION, "ibuprofeno"));

        assertThat(consultation.items()).hasSize(1);
        assertThatIllegalArgumentException().isThrownBy(() -> withNotes("notas", nullItem()));
    }

    private static List<ConsultationItem> nullItem() {
        List<ConsultationItem> items = new ArrayList<>();
        items.add(null);
        return items;
    }
}
