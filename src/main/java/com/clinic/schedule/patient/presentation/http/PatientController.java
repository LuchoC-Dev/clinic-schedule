package com.clinic.schedule.patient.presentation.http;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.clinic.schedule.patient.entity.Patient;
import com.clinic.schedule.patient.service.PatientService;

/**
 * The patient endpoints: register, correct, look up.
 *
 * <p>Updating is a {@code PUT} and not a {@code POST} on a sub-path, unlike cancelling an
 * appointment (D-23): correcting a misspelled name or adding the document that was missing
 * is an edit to the record, not a business operation with rules of its own.
 *
 * <p>There is no delete. A patient with appointments cannot disappear without taking history
 * with them, and nothing in v1 asks for it.
 */
@RestController
@RequestMapping("/api/patients")
class PatientController {

    private final PatientService patients;

    PatientController(PatientService patients) {
        this.patients = patients;
    }

    @PostMapping
    ResponseEntity<PatientResponse> register(@Valid @RequestBody PatientRequest request) {
        return created(patients.register(request.fullName(), request.toDocument(), request.seen()));
    }

    /** A patient who gives no name: registered under an alias the answer carries (D-60). */
    @PostMapping("/unidentified")
    ResponseEntity<PatientResponse> registerUnidentified() {
        return created(patients.registerUnidentified());
    }

    /** Corrects who the patient is; the change goes to their identity history (D-60). */
    @PutMapping("/{id}")
    PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientRequest request) {
        return PatientResponse.of(patients.changeIdentity(id, request.fullName(), request.toDocument(), request.seen()));
    }

    /** The history of the patient's identity, the most recent change first. */
    @GetMapping("/{id}/identity-changes")
    List<IdentityChangeResponse> identityChanges(@PathVariable UUID id) {
        return patients.identityHistory(id).stream().map(IdentityChangeResponse::of).toList();
    }

    @GetMapping("/{id}")
    PatientResponse byId(@PathVariable UUID id) {
        return PatientResponse.of(patients.byId(id));
    }

    /** Reception's search box: part of a name, the document number or an alias. No query lists everyone. */
    @GetMapping
    List<PatientResponse> search(@RequestParam(required = false) String query) {
        return patients.searchPatients(query).stream().map(PatientResponse::of).toList();
    }

    private static ResponseEntity<PatientResponse> created(Patient registered) {
        return ResponseEntity
                .created(URI.create("/api/patients/" + registered.getId()))
                .body(PatientResponse.of(registered));
    }
}
