package com.clinic.schedule.clinicalrecord.adapter.in.http;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.clinicalrecord.application.usecase.AuthenticateRecordCopyUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.DeliverRecordCopyUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ListRecordCopiesUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.RequestAccessUseCase;
import com.clinic.schedule.clinicalrecord.application.usecase.ViewRecordCopyUseCase;
import com.clinic.schedule.clinicalrecord.domain.AccessGrant;
import com.clinic.schedule.clinicalrecord.domain.RecordCopy;
import com.clinic.schedule.clinicalrecord.domain.RecordCopyId;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.practitioner.api.PractitionerReader;
import com.clinic.schedule.practitioner.api.PractitionerSummary;

/**
 * The copies of records the patients asked for (D-95). The practitioners' routes, {@code /api/record-copies/**}:
 * those waiting with the time left, one by its id or by its code, its PDF — a reading —, putting it together
 * again, authenticating it, and asking for access to its record to do so. Reception's,
 * {@code /api/record-copy-envelopes/**}: the envelopes on paper waiting to be collected, and handing one over —
 * who and when, never what it holds (D-51).
 */
@RestController
class RecordCopyController {

    private final ListRecordCopiesUseCase list;
    private final ViewRecordCopyUseCase view;
    private final AuthenticateRecordCopyUseCase authenticate;
    private final DeliverRecordCopyUseCase deliver;
    private final RequestAccessUseCase requestAccess;
    private final PatientReader patients;
    private final PractitionerReader practitioners;
    private final Clock clock;

    RecordCopyController(ListRecordCopiesUseCase list, ViewRecordCopyUseCase view,
            AuthenticateRecordCopyUseCase authenticate, DeliverRecordCopyUseCase deliver,
            RequestAccessUseCase requestAccess, PatientReader patients, PractitionerReader practitioners, Clock clock) {
        this.list = list;
        this.view = view;
        this.authenticate = authenticate;
        this.deliver = deliver;
        this.requestAccess = requestAccess;
        this.patients = patients;
        this.practitioners = practitioners;
        this.clock = clock;
    }

    /** Those waiting for a practitioner, the soonest due first. */
    @GetMapping(value = "/api/record-copies", params = "!code")
    List<RecordCopyResponse> waiting() {
        List<ListRecordCopiesUseCase.Waiting> found = list.waiting();
        return responses(found.stream().map(ListRecordCopiesUseCase.Waiting::copy).toList(),
                found.stream().collect(Collectors.toMap(waiting -> waiting.copy().id(),
                        ListRecordCopiesUseCase.Waiting::mayRead)));
    }

    /** The copy a code printed on paper belongs to: to check a copy against the application. */
    @GetMapping(value = "/api/record-copies", params = "code")
    RecordCopyResponse byCode(@RequestParam String code) {
        return response(view.findByCode(code));
    }

    @GetMapping("/api/record-copies/{id}")
    RecordCopyResponse one(@PathVariable UUID id) {
        return response(view.find(new RecordCopyId(id)));
    }

    /**
     * The copy as a PDF, as the record stood when it was put together: a reading of the record. Shown in the browser,
     * to review it or print it (D-106).
     */
    @GetMapping("/api/record-copies/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("copia-historia-clinica.pdf").build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(view.document(new RecordCopyId(id)).content());
    }

    @PostMapping("/api/record-copies/{id}/reassembly")
    RecordCopyResponse reassemble(@PathVariable UUID id) {
        return response(new ViewRecordCopyUseCase.View(authenticate.reassemble(new RecordCopyId(id)), true));
    }

    @PostMapping("/api/record-copies/{id}/authentication")
    RecordCopyResponse authenticate(@PathVariable UUID id, @RequestBody AuthenticateRequest request) {
        return response(new ViewRecordCopyUseCase.View(authenticate.authenticate(new RecordCopyId(id),
                request.assembly(), Boolean.TRUE.equals(request.reviewed())), true));
    }

    /** Access to the record, to authenticate its copy: granted at once and recorded, as for a check (D-54). */
    @PostMapping("/api/record-copies/{id}/access-grants")
    ResponseEntity<AccessGrantResponse> requestAccess(@PathVariable UUID id,
            @RequestBody(required = false) RequestAccessRequest request) {
        AccessGrant grant = requestAccess.forCopy(new RequestAccessUseCase.CopyCommand(new RecordCopyId(id),
                request == null ? null : request.reason()));
        return ResponseEntity.status(201).body(AccessGrantResponse.of(grant, names()));
    }

    /** The envelopes on paper waiting at reception. */
    @GetMapping("/api/record-copy-envelopes")
    List<EnvelopeResponse> envelopes() {
        List<RecordCopy> found = list.awaitingHandover();
        Map<UUID, PatientSummary> people = people(found);
        return found.stream().map(copy -> EnvelopeResponse.of(copy, people)).toList();
    }

    @PostMapping("/api/record-copy-envelopes/{id}/handover")
    EnvelopeResponse handOver(@PathVariable UUID id, @RequestBody HandoverRequest request) {
        RecordCopy copy = deliver.handOver(new RecordCopyId(id), Boolean.TRUE.equals(request.documentSeen()));
        return EnvelopeResponse.of(copy, people(List.of(copy)));
    }

    private RecordCopyResponse response(ViewRecordCopyUseCase.View found) {
        return responses(List.of(found.copy()), Map.of(found.copy().id(), found.mayRead())).getFirst();
    }

    private List<RecordCopyResponse> responses(List<RecordCopy> copies, Map<RecordCopyId, Boolean> mayRead) {
        Map<UUID, PatientSummary> people = people(copies);
        Map<UUID, String> names = names();
        LocalDateTime now = LocalDateTime.now(clock);
        return copies.stream()
                .map(copy -> RecordCopyResponse.of(copy, mayRead.get(copy.id()), now, people, names))
                .toList();
    }

    private Map<UUID, PatientSummary> people(List<RecordCopy> copies) {
        return patients.findAll(copies.stream()
                        .flatMap(copy -> Stream.of(copy.patientId().value(), copy.requestedBy()))
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PatientSummary::id, Function.identity()));
    }

    private Map<UUID, String> names() {
        return practitioners.findAll().stream()
                .collect(Collectors.toMap(PractitionerSummary::id, PractitionerSummary::fullName));
    }

    /**
     * @param assembly the assembly the practitioner reviewed
     * @param reviewed that they reviewed it is complete
     */
    record AuthenticateRequest(int assembly, Boolean reviewed) {
    }

    /** @param documentSeen that reception saw the document of whoever collects it */
    record HandoverRequest(Boolean documentSeen) {
    }
}
