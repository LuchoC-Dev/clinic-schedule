package com.clinic.schedule.patient.service;

import java.util.Collection;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException.Reason;
import com.clinic.schedule.patient.api.PatientRegistrationRefusedException;
import com.clinic.schedule.patient.api.PatientSummary;
import com.clinic.schedule.patient.api.PatientWriter;
import com.clinic.schedule.patient.entity.DocumentType;
import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.IncompleteDocumentException;
import com.clinic.schedule.patient.entity.Patient;
import com.clinic.schedule.patient.persistence.PatientRepository;

/**
 * Registering and finding patients, and answering other modules through
 * {@link PatientReader} and {@link PatientWriter}. It is public because the presentation layer
 * lives in another package; other modules still reach it only as those interfaces, which
 * {@code ArchitectureTest} checks now that visibility no longer can (D-24, D-42).
 *
 * <p>The document is checked here before saving so the answer is a clear 409 instead of a
 * constraint violation, but the constraint in the database is the one that actually
 * guarantees it (D-09). Under a race the insert fails, which is the correct outcome.
 *
 * <p>Each method declares its own transaction and every write calls {@code save()}, even
 * where Hibernate would write the change by itself (D-27).
 */
@Service
public class PatientService implements PatientReader, PatientWriter {

    private final PatientRepository patients;

    PatientService(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional
    public Patient register(String fullName, IdentityDocument document) {
        requireDocumentIsFree(document);
        return patients.save(Patient.register(fullName, document));
    }

    /**
     * Registration as other modules ask for it, in plain values. This module's own refusals are
     * translated into the one the api declares, so the caller need not know them.
     */
    @Override
    @Transactional
    public UUID register(String fullName, String documentType, String documentNumber) {
        try {
            DocumentType type = documentType == null || documentType.isBlank()
                    ? null : DocumentType.valueOf(documentType);
            return register(fullName, IdentityDocument.ofNullable(type, documentNumber)).getId();
        } catch (DuplicateDocumentException e) {
            throw new PatientRegistrationRefusedException(Reason.DUPLICATE_DOCUMENT, e.getMessage());
        } catch (IncompleteDocumentException e) {
            throw new PatientRegistrationRefusedException(Reason.INCOMPLETE_DOCUMENT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new PatientRegistrationRefusedException(Reason.INVALID_DATA, e.getMessage());
        }
    }

    @Transactional
    public Patient update(UUID id, String fullName, IdentityDocument document) {
        Patient patient = patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));

        patient.rename(fullName);
        if (!Objects.equals(document, patient.getDocument())) {
            requireDocumentIsFree(document);
            patient.setDocument(document);
        }
        return patients.save(patient);
    }

    @Transactional(readOnly = true)
    public Patient byId(UUID id) {
        return patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
    }

    /**
     * Reception's search box: part of a name or the document number. An empty search lists
     * everyone, which is usable because a small practice has hundreds of patients, not
     * thousands.
     */
    @Transactional(readOnly = true)
    public List<Patient> searchPatients(String query) {
        if (query == null || query.isBlank()) {
            return patients.findAllByOrderByFullNameAsc();
        }
        String term = query.strip();
        return patients.findByFullNameContainingIgnoreCaseOrDocumentNumberContainingOrderByFullNameAsc(term, term);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID patientId) {
        return patients.existsById(patientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientSummary> search(String query) {
        return summaries(searchPatients(query));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientSummary> findAll(Collection<UUID> patientIds) {
        return summaries(patients.findAllById(patientIds));
    }

    @Override
    public List<String> documentTypes() {
        return Arrays.stream(DocumentType.values()).map(DocumentType::name).toList();
    }

    private static List<PatientSummary> summaries(List<Patient> found) {
        return found.stream()
                .map(patient -> new PatientSummary(patient.getId(), patient.getFullName(), label(patient.getDocument())))
                .toList();
    }

    private static String label(IdentityDocument document) {
        return document == null ? null : document.type() + " " + document.number();
    }

    private void requireDocumentIsFree(IdentityDocument document) {
        if (document != null && patients.existsByDocument(document)) {
            throw new DuplicateDocumentException(document);
        }
    }
}
