package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.portal.application.port.PatientAccountRepository;
import com.clinic.schedule.portal.domain.PatientAccount;

/** Implements the port over JPA. */
@Repository
class PatientAccountJpaAdapter implements PatientAccountRepository {

    private final PatientAccountRowRepository rows;

    PatientAccountJpaAdapter(PatientAccountRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(PatientAccount account) {
        rows.save(PatientAccountRow.from(account));
    }

    @Override
    public Optional<PatientAccount> findById(UUID id) {
        return rows.findById(id).map(PatientAccountRow::toAccount);
    }

    @Override
    public Optional<PatientAccount> findByPatientId(UUID patientId) {
        return rows.findByPatientId(patientId).map(PatientAccountRow::toAccount);
    }
}
