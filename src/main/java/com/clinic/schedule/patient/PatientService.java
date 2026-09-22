package com.clinic.schedule.patient;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registering and finding patients. Package-private: nothing outside the module reaches it,
 * which is what keeps the module's surface down to its {@code api/} (D-24).
 *
 * <p>The document is checked here before saving so the answer is a clear 409 instead of a
 * constraint violation, but the constraint in the database is the one that actually
 * guarantees it (D-09). Under a race the insert fails, which is the correct outcome.
 */
@Service
@Transactional
class PatientService {

    private final PatientRepository patients;

    PatientService(PatientRepository patients) {
        this.patients = patients;
    }

    Patient register(String fullName, IdentityDocument document) {
        requireDocumentIsFree(document);
        return patients.save(Patient.register(fullName, document));
    }

    Patient update(UUID id, String fullName, IdentityDocument document) {
        Patient patient = patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));

        patient.rename(fullName);
        if (!Objects.equals(document, patient.getDocument())) {
            requireDocumentIsFree(document);
            patient.setDocument(document);
        }
        return patient;
    }

    @Transactional(readOnly = true)
    Patient byId(UUID id) {
        return patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
    }

    /**
     * Reception's search box: part of a name or the document number. An empty search lists
     * everyone, which is usable because a small practice has hundreds of patients, not
     * thousands.
     */
    @Transactional(readOnly = true)
    List<Patient> search(String query) {
        if (query == null || query.isBlank()) {
            return patients.findAllByOrderByFullNameAsc();
        }
        String term = query.strip();
        return patients.findByFullNameContainingIgnoreCaseOrDocumentNumberContainingOrderByFullNameAsc(term, term);
    }

    private void requireDocumentIsFree(IdentityDocument document) {
        if (document != null && patients.existsByDocument(document)) {
            throw new DuplicateDocumentException(document);
        }
    }
}
