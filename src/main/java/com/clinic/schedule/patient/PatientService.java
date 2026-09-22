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
 * <p>The national ID is checked here before saving so the answer is a clear 409 instead of a
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

    Patient register(String fullName, String nationalId) {
        Patient patient = Patient.register(fullName, nationalId);
        requireNationalIdIsFree(patient.getNationalId());
        return patients.save(patient);
    }

    Patient update(UUID id, String fullName, String nationalId) {
        Patient patient = patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));

        patient.rename(fullName);
        String normalized = Patient.normalizeNationalId(nationalId);
        if (!Objects.equals(normalized, patient.getNationalId())) {
            requireNationalIdIsFree(normalized);
            patient.setNationalId(normalized);
        }
        return patient;
    }

    @Transactional(readOnly = true)
    Patient byId(UUID id) {
        return patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
    }

    /**
     * Reception's search box: part of a name or the national ID. An empty search lists
     * everyone, which is usable because a small practice has hundreds of patients, not
     * thousands.
     */
    @Transactional(readOnly = true)
    List<Patient> search(String query) {
        if (query == null || query.isBlank()) {
            return patients.findAllByOrderByFullNameAsc();
        }
        String term = query.strip();
        return patients.findByFullNameContainingIgnoreCaseOrNationalIdContainingOrderByFullNameAsc(term, term);
    }

    private void requireNationalIdIsFree(String nationalId) {
        if (nationalId != null && patients.existsByNationalId(nationalId)) {
            throw new DuplicateNationalIdException(nationalId);
        }
    }
}
