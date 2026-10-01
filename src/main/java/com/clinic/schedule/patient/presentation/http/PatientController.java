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
 * The patient endpoints: register, correct who they are and what else is known of them, look up.
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
    ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        return created(patients.register(request.fullName(), request.toDocument(), request.seen(),
                request.toDetails()));
    }

    /** A patient who gives no name: registered under an alias the answer carries (D-60). */
    @PostMapping("/unidentified")
    ResponseEntity<PatientResponse> registerUnidentified() {
        return created(patients.registerUnidentifiedPatient());
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

    /**
     * Changes what is known of the patient besides who they are — apart from their identity, which
     * has a history of its own; each detail that changed goes to theirs (D-73).
     */
    @PutMapping("/{id}/details")
    PatientResponse updateDetails(@PathVariable UUID id, @RequestBody PatientDetailsRequest request) {
        return PatientResponse.of(patients.changeDetails(id, request.toDetails()));
    }

    /**
     * Who agreed to the patient being reminded on each channel, as it is to be; one left out is withdrawn
     * (D-77).
     */
    @PutMapping("/{id}/contact-consent")
    PatientResponse updateConsent(@PathVariable UUID id, @RequestBody ContactConsentRequest request) {
        return PatientResponse.of(patients.changeConsent(id, request.phone(), request.email()));
    }

    /** The history of the patient's details, the most recent change first. */
    @GetMapping("/{id}/details-changes")
    List<DetailsChangeResponse> detailsChanges(@PathVariable UUID id) {
        return patients.detailsHistory(id).stream().map(DetailsChangeResponse::of).toList();
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
