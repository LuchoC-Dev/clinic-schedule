package com.clinic.schedule.portal.adapter.in.http;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.portal.application.usecase.OwnRecordCopiesUseCase;
import com.clinic.schedule.portal.domain.AccountHolder;
import com.clinic.schedule.portal.domain.RecordCopyRequest;

/**
 * A patient's copies of clinical records on the web (D-95): whose record they may ask a copy of, asking for one,
 * following them, and downloading the PDF once authenticated. Always for the patient logged in (D-80).
 */
@RestController
class PortalRecordCopyController {

    private final OwnRecordCopiesUseCase copies;

    PortalRecordCopyController(OwnRecordCopiesUseCase copies) {
        this.copies = copies;
    }

    /** Whose record the patient may ask a copy of, and the requests of those records. */
    @GetMapping("/api/portal/record-copies")
    RecordCopiesResponse list() {
        return new RecordCopiesResponse(
                copies.askableFor().stream().map(person -> new Person(person.patientId(), person.fullName())).toList(),
                copies.list().stream().map(PortalRecordCopyController::copy).toList());
    }

    /** {@code patientId} absent for the patient's own record. */
    @PostMapping("/api/portal/record-copies")
    ResponseEntity<Copy> request(@RequestBody Request request) {
        RecordCopyRequest copy = copies.request(request.patientId(), request.delivery());
        return ResponseEntity.created(URI.create("/api/portal/record-copies/" + copy.id() + "/pdf"))
                .body(copy(new OwnRecordCopiesUseCase.Entry(copy, null, copy.patientId().equals(copy.requestedBy()))));
    }

    @GetMapping("/api/portal/record-copies/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("copia-historia-clinica.pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(copies.download(id));
    }

    private static Copy copy(OwnRecordCopiesUseCase.Entry entry) {
        RecordCopyRequest copy = entry.copy();
        AccountHolder patient = entry.patient();
        return new Copy(copy.id(), new Person(copy.patientId(), patient == null ? null : patient.fullName()),
                copy.requestedAt(), copy.delivery(), copy.state(), copy.deadline(), copy.authenticatedAt(),
                copy.handedOverAt(), entry.own());
    }

    record Request(UUID patientId, RecordCopyRequest.Delivery delivery) {
    }

    record Person(UUID id, String fullName) {
    }

    /**
     * A request as the patient follows it: nothing of the record, nor who authenticated it; {@code own} when it is of
     * the patient's own record (D-112).
     */
    record Copy(UUID id, Person patient, LocalDateTime requestedAt, RecordCopyRequest.Delivery delivery,
            RecordCopyRequest.State state, LocalDateTime deadline, LocalDateTime authenticatedAt,
            LocalDateTime handedOverAt, boolean own) {
    }

    record RecordCopiesResponse(List<Person> askableFor, List<Copy> copies) {
    }
}
