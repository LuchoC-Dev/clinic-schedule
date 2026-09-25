package com.clinic.schedule.clinicalrecord.domain;

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

    // --- addenda ------------------------------------------------------------------------

    private static final PractitionerId ANOTHER = new PractitionerId(UUID.randomUUID());

    private static Addendum addendum(PractitionerId author, LocalDateTime at, String text) {
        return new Addendum(AddendumId.newId(), author, at, text);
    }

    @Test
    void anAddendumIsAddedAtTheEndAndChangesNothingElse() {
        Consultation sent = withNotes("Control.", List.of(new ConsultationItem(ItemKind.PRESCRIPTION, "enalapril 10 mg")));

        Consultation once = sent.withAddendum(addendum(PRACTITIONER, AT.plusHours(1), "  La dosis es 5 mg.  "));
        Consultation twice = once.withAddendum(addendum(ANOTHER, AT.plusDays(3), "Tolera bien la medicación."));

        assertThat(sent.addenda()).isEmpty();
        assertThat(twice.addenda()).extracting(Addendum::text)
                .containsExactly("La dosis es 5 mg.", "Tolera bien la medicación.");
        assertThat(twice.addenda().get(1).author()).isEqualTo(ANOTHER);
        assertThat(twice.notes()).isEqualTo(sent.notes());
        assertThat(twice.items()).isEqualTo(sent.items());
        assertThat(twice.recordedAt()).isEqualTo(sent.recordedAt());
    }

    @Test
    void anAddendumIsWrittenAfterTheConsultationAndTheAddendaBeforeIt() {
        Consultation sent = withNotes("Control.", List.of());
        Consultation later = sent.withAddendum(addendum(PRACTITIONER, AT.plusDays(2), "Primera."));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> sent.withAddendum(addendum(PRACTITIONER, AT.minusMinutes(1), "Antes.")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> later.withAddendum(addendum(PRACTITIONER, AT.plusDays(1), "Fuera de orden.")));
        assertThat(sent.withAddendum(addendum(PRACTITIONER, AT, "En el momento.")).addenda()).hasSize(1);
    }

    @Test
    void theSameAddendumIsAddedOnce() {
        Addendum note = addendum(PRACTITIONER, AT.plusHours(1), "Una vez.");
        Consultation added = withNotes("Control.", List.of()).withAddendum(note);

        assertThat(added.hasAddendum(note.id())).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> added.withAddendum(note));
    }

    @Test
    void anAddendumNeedsATextOfAtMostTwoThousandCharacters() {
        assertThatIllegalArgumentException().isThrownBy(() -> addendum(PRACTITIONER, AT, "  "));
        assertThatIllegalArgumentException().isThrownBy(() -> addendum(PRACTITIONER, AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> addendum(null, AT, "texto"));
        assertThatIllegalArgumentException().isThrownBy(() -> addendum(PRACTITIONER, null, "texto"));
        assertThat(addendum(PRACTITIONER, AT, "a".repeat(2_000)).text()).hasSize(2_000);
        assertThatIllegalArgumentException().isThrownBy(() -> addendum(PRACTITIONER, AT, "a".repeat(2_001)));
    }

    @Test
    void aStoredConsultationIsCheckedWithItsAddenda() {
        Addendum first = addendum(PRACTITIONER, AT.plusDays(2), "Primera.");
        Addendum second = addendum(ANOTHER, AT.plusDays(1), "Fuera de orden.");

        assertThat(Consultation.restore(ID, PATIENT, PRACTITIONER, AT, "notas", List.of(), List.of(first)).addenda())
                .containsExactly(first);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Consultation.restore(ID, PATIENT, PRACTITIONER, AT, "notas", List.of(),
                        List.of(first, second)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Consultation.restore(ID, PATIENT, PRACTITIONER, AT, "notas", List.of(), null));
    }

    private static List<ConsultationItem> nullItem() {
        List<ConsultationItem> items = new ArrayList<>();
        items.add(null);
        return items;
    }
}
