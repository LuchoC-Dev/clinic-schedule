package com.clinic.schedule.portal.adapter.out.external;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.portal.application.port.AccountHolderPort;
import com.clinic.schedule.portal.domain.AccountHolder;

/** Answers {@link AccountHolderPort} by asking the patient module through its api (D-05). */
@Component
class AccountHolderAdapter implements AccountHolderPort {

    private final PatientReader patients;

    AccountHolderAdapter(PatientReader patients) {
        this.patients = patients;
    }

    @Override
    public Optional<AccountHolder> find(UUID patientId) {
        return findAll(List.of(patientId)).stream().findFirst();
    }

    @Override
    public Optional<UUID> findByDocument(String documentType, String documentNumber) {
        return patients.findIdByDocument(documentType, documentNumber);
    }

    @Override
    public List<AccountHolder> findAll(Collection<UUID> patientIds) {
        return patients.findAll(patientIds).stream()
                .map(patient -> new AccountHolder(patient.id(), patient.fullName(), patient.identityVerified(),
                        patient.birthDate(), patient.registeredOnWeb()))
                .toList();
    }
}
