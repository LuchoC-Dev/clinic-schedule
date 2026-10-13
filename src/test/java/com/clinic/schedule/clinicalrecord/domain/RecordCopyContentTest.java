package com.clinic.schedule.clinicalrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinic.schedule.clinicalrecord.domain.RecordCopyContent.CopiedCheck;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyContent.CopiedConsultation;

/** The copy is the record as it stood at a time: what was written by then, and nothing written later (D-95). */
class RecordCopyContentTest {

    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final PatientId SOMEONE_ELSE = new PatientId(UUID.randomUUID());
    private static final PractitionerId ARIAS = new PractitionerId(UUID.randomUUID());
    private static final PractitionerId BENITEZ = new PractitionerId(UUID.randomUUID());
    private static final LocalDateTime MARCH = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final LocalDateTime COPIED = LocalDateTime.of(2026, 3, 20, 12, 0);
    private static final byte[] PDF = "%PDF-1.4\n% informe\n".getBytes(StandardCharsets.US_ASCII);

    private static Addendum addendum(LocalDateTime at, String text) {
        return new Addendum(AddendumId.newId(), BENITEZ, at, text);
    }

    private static Attachment file(AttachmentOwner owner, PatientId patient, LocalDateTime at, String name) {
        return Attachment.upload(AttachmentId.newId(), patient, owner, ARIAS, at, name, PDF);
    }

    @Test
    void whatWasWrittenUpToThenIsCopiedAndNothingLater() {
        ConsultationId earlyId = ConsultationId.newId();
        Consultation early = Consultation.restore(earlyId, PATIENT, ARIAS, MARCH, "Control.",
                List.of(new ConsultationItem(ItemKind.PRESCRIPTION, "enalapril 10 mg")),
                List.of(addendum(MARCH.plusDays(1), "La dosis es 5 mg."), addendum(COPIED.plusMinutes(1), "Después.")));
        Consultation late = Consultation.record(ConsultationId.newId(), PATIENT, ARIAS, COPIED.plusDays(1), "Después.",
                List.of());
        Consultation others = Consultation.record(ConsultationId.newId(), SOMEONE_ELSE, ARIAS, MARCH, "Ajena.",
                List.of());

        Check reportedThen = Check.restore(CheckId.newId(), PATIENT, ARIAS, MARCH, "Presión", earlyId,
                new CheckResult(ARIAS, MARCH, "120/80"));
        Check reportedLater = Check.restore(CheckId.newId(), PATIENT, ARIAS, MARCH.plusDays(2), "Hemograma", null,
                new CheckResult(BENITEZ, COPIED.plusHours(1), "Normal."));
        Check doneLater = Check.perform(CheckId.newId(), PATIENT, ARIAS, COPIED.plusSeconds(1), "Glucemia", null);
        Check othersCheck = Check.perform(CheckId.newId(), SOMEONE_ELSE, ARIAS, MARCH, "Ajeno", null);

        Attachment ofConsultation = file(new AttachmentOwner.OfConsultation(earlyId), PATIENT, MARCH, "foto.pdf");
        Attachment ofCheck = file(new AttachmentOwner.OfCheck(reportedLater.id()), PATIENT, MARCH.plusDays(3), "lab.pdf");
        Attachment paper = file(new AttachmentOwner.OfRecord(PATIENT), PATIENT, MARCH.plusDays(4), "papel.pdf");
        Attachment uploadedLater = file(new AttachmentOwner.OfRecord(PATIENT), PATIENT, COPIED.plusMinutes(5), "tarde.pdf");
        Attachment othersFile = file(new AttachmentOwner.OfRecord(SOMEONE_ELSE), SOMEONE_ELSE, MARCH, "ajeno.pdf");

        RecordCopyContent copy = RecordCopyContent.asOf(COPIED, PATIENT, List.of(late, others, early),
                List.of(doneLater, othersCheck, reportedLater, reportedThen), List.of(),
                List.of(uploadedLater, paper, ofCheck, othersFile, ofConsultation));

        assertThat(copy.asOf()).isEqualTo(COPIED);
        assertThat(copy.consultations()).hasSize(1);
        CopiedConsultation consultation = copy.consultations().getFirst();
        assertThat(consultation.consultation()).isEqualTo(early);
        assertThat(consultation.addenda()).extracting(Addendum::text).containsExactly("La dosis es 5 mg.");
        assertThat(consultation.checks()).extracting(CopiedCheck::check).containsExactly(reportedThen);
        assertThat(consultation.checks().getFirst().result()).isPresent();
        assertThat(consultation.attachments()).containsExactly(ofConsultation);

        assertThat(copy.checksWithoutConsultation()).hasSize(1);
        CopiedCheck alone = copy.checksWithoutConsultation().getFirst();
        assertThat(alone.check()).isEqualTo(reportedLater);
        assertThat(alone.result()).isEmpty();
        assertThat(alone.attachments()).containsExactly(ofCheck);

        assertThat(copy.recordFiles()).containsExactly(paper);
        assertThat(copy.attachments()).containsExactly(ofConsultation, ofCheck, paper);
        assertThat(copy.isEmpty()).isFalse();
    }

    @Test
    void anOrderIsCopiedAsItStoodThen() {
        ConsultationId consultationId = ConsultationId.newId();
        Consultation consultation = Consultation.record(consultationId, PATIENT, ARIAS, MARCH, "Control.", List.of());
        Check fulfilledThen = Check.perform(CheckId.newId(), PATIENT, BENITEZ, MARCH.plusDays(1), "Hemograma", null);
        Check fulfilledLater = Check.perform(CheckId.newId(), PATIENT, BENITEZ, COPIED.plusDays(1), "Glucemia", null);
        CheckOrder open = CheckOrder.issue(CheckOrderId.newId(), PATIENT, ARIAS, MARCH, consultationId, "Ecografía");
        CheckOrder fulfilled = CheckOrder.restore(CheckOrderId.newId(), PATIENT, ARIAS, MARCH, consultationId,
                "Hemograma", new OrderOutcome.Fulfilled(fulfilledThen.id()));
        CheckOrder fulfilledAfter = CheckOrder.restore(CheckOrderId.newId(), PATIENT, ARIAS, MARCH, consultationId,
                "Glucemia", new OrderOutcome.Fulfilled(fulfilledLater.id()));
        CheckOrder cancelled = CheckOrder.restore(CheckOrderId.newId(), PATIENT, ARIAS, MARCH, consultationId,
                "Radiografía", new OrderOutcome.Cancelled(MARCH.plusDays(2), "Trajo una reciente"));
        CheckOrder cancelledAfter = CheckOrder.restore(CheckOrderId.newId(), PATIENT, ARIAS, MARCH, consultationId,
                "Tomografía", new OrderOutcome.Cancelled(COPIED.plusDays(2), "No hace falta"));

        RecordCopyContent copy = RecordCopyContent.asOf(COPIED, PATIENT, List.of(consultation),
                List.of(fulfilledThen, fulfilledLater),
                List.of(open, fulfilled, fulfilledAfter, cancelled, cancelledAfter), List.of());

        assertThat(copy.consultations().getFirst().orders())
                .extracting(order -> order.order().description() + " " + order.status())
                .containsExactly("Ecografía OPEN", "Hemograma FULFILLED", "Glucemia OPEN", "Radiografía CANCELLED",
                        "Tomografía OPEN");
    }

    @Test
    void aRecordWithNothingWrittenIsAnEmptyCopy() {
        RecordCopyContent copy = RecordCopyContent.asOf(COPIED, PATIENT, List.of(), List.of(), List.of(), List.of());

        assertThat(copy.isEmpty()).isTrue();
        assertThat(copy.attachments()).isEmpty();
    }
}
