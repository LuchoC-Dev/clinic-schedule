package com.clinic.schedule.clinicalrecord.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What a copy of a clinical record holds: the whole record as it stood at a time (D-95) — every consultation,
 * check, result, order, addendum and attached file written up to then, from every practitioner, each with its
 * author and when it was written. What came later is left out: a result reported afterwards, an addendum, an
 * order fulfilled or cancelled since. The accesses granted are not clinical content, and are not copied.
 *
 * <p>Put together from the record itself whenever the copy is shown, so the same time always gives the same
 * copy: nothing in the record is changed or deleted (D-47, D-52, D-53, D-57). A domain service: it is handed
 * the record and reads nothing.
 *
 * @param consultations the oldest first, each with what belongs to it
 * @param checksWithoutConsultation the oldest first
 * @param recordFiles the files of the record as a whole (D-94), the oldest first
 */
public record RecordCopyContent(LocalDateTime asOf, List<CopiedConsultation> consultations,
        List<CopiedCheck> checksWithoutConsultation, List<Attachment> recordFiles) {

    /** A consultation, with its addenda, its checks, its orders and its files, as they stood. */
    public record CopiedConsultation(Consultation consultation, List<Addendum> addenda, List<CopiedCheck> checks,
            List<CopiedOrder> orders, List<Attachment> attachments) {
    }

    /** A check, with its result if it was reported by then, its addenda and its files. */
    public record CopiedCheck(Check check, Optional<CheckResult> result, List<Addendum> addenda,
            List<Attachment> attachments) {
    }

    /** An order, and where it stood: open, fulfilled or cancelled by then. */
    public record CopiedOrder(CheckOrder order, OrderStatus status, Optional<OrderOutcome> outcome) {
    }

    /** The record of one patient as it stood at {@code at}; what the collections hold of others is ignored. */
    public static RecordCopyContent asOf(LocalDateTime at, PatientId patientId, Collection<Consultation> consultations,
            Collection<Check> checks, Collection<CheckOrder> orders, Collection<Attachment> attachments) {
        List<Check> checksThen = checks.stream()
                .filter(check -> check.patientId().equals(patientId) && !check.performedAt().isAfter(at))
                .sorted(Comparator.comparing(Check::performedAt))
                .toList();
        Map<CheckId, Check> checksById = checksThen.stream().collect(Collectors.toMap(Check::id, Function.identity()));
        List<Attachment> attachmentsThen = attachments.stream()
                .filter(attachment -> attachment.patientId().equals(patientId) && !attachment.uploadedAt().isAfter(at))
                .sorted(Comparator.comparing(Attachment::uploadedAt))
                .toList();
        List<CheckOrder> ordersThen = orders.stream()
                .filter(order -> order.patientId().equals(patientId) && !order.orderedAt().isAfter(at))
                .sorted(Comparator.comparing(CheckOrder::orderedAt))
                .toList();

        List<CopiedConsultation> copiedConsultations = consultations.stream()
                .filter(consultation -> consultation.patientId().equals(patientId)
                        && !consultation.recordedAt().isAfter(at))
                .sorted(Comparator.comparing(Consultation::recordedAt))
                .map(consultation -> new CopiedConsultation(consultation,
                        addendaUntil(consultation.addenda(), at),
                        checksThen.stream()
                                .filter(check -> check.consultationId().filter(consultation.id()::equals).isPresent())
                                .map(check -> copied(check, at, attachmentsThen))
                                .toList(),
                        ordersThen.stream()
                                .filter(order -> order.consultationId().equals(consultation.id()))
                                .map(order -> copied(order, at, checksById))
                                .toList(),
                        ownedBy(attachmentsThen, new AttachmentOwner.OfConsultation(consultation.id()))))
                .toList();
        List<CopiedCheck> alone = checksThen.stream()
                .filter(check -> check.consultationId().isEmpty())
                .map(check -> copied(check, at, attachmentsThen))
                .toList();
        return new RecordCopyContent(at, copiedConsultations, alone,
                ownedBy(attachmentsThen, new AttachmentOwner.OfRecord(patientId)));
    }

    /** Every file the copy holds, in the order they were uploaded: what is appended after the entries. */
    public List<Attachment> attachments() {
        return Stream.of(
                        consultations.stream().flatMap(consultation -> Stream.concat(
                                consultation.attachments().stream(),
                                consultation.checks().stream().flatMap(check -> check.attachments().stream()))),
                        checksWithoutConsultation.stream().flatMap(check -> check.attachments().stream()),
                        recordFiles.stream())
                .flatMap(Function.identity())
                .sorted(Comparator.comparing(Attachment::uploadedAt))
                .toList();
    }

    /** Whether there is nothing at all to copy. */
    public boolean isEmpty() {
        return consultations.isEmpty() && checksWithoutConsultation.isEmpty() && recordFiles.isEmpty();
    }

    private static CopiedCheck copied(Check check, LocalDateTime at, List<Attachment> attachments) {
        return new CopiedCheck(check,
                check.result().filter(result -> !result.writtenAt().isAfter(at)),
                addendaUntil(check.addenda(), at),
                ownedBy(attachments, new AttachmentOwner.OfCheck(check.id())));
    }

    private static CopiedOrder copied(CheckOrder order, LocalDateTime at, Map<CheckId, Check> checksThen) {
        Optional<OrderOutcome> outcome = order.outcome().filter(happened -> switch (happened) {
            case OrderOutcome.Fulfilled fulfilled -> checksThen.containsKey(fulfilled.check());
            case OrderOutcome.Cancelled cancelled -> !cancelled.at().isAfter(at);
        });
        return new CopiedOrder(order, outcome.map(OrderOutcome::status).orElse(OrderStatus.OPEN), outcome);
    }

    private static List<Addendum> addendaUntil(List<Addendum> addenda, LocalDateTime at) {
        return addenda.stream().filter(addendum -> !addendum.writtenAt().isAfter(at)).toList();
    }

    private static List<Attachment> ownedBy(List<Attachment> attachments, AttachmentOwner owner) {
        return attachments.stream().filter(attachment -> attachment.owner().equals(owner)).toList();
    }
}
