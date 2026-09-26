package com.clinic.schedule.patient.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.patient.entity.IdentityDocument;
import com.clinic.schedule.patient.entity.Patient;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /** Matches the whole document: the type and the number together (see IdentityDocument). */
    Optional<Patient> findByDocument(IdentityDocument document);

    boolean existsByDocument(IdentityDocument document);

    /**
     * One search box for reception: part of a name, the document number, or the alias a patient
     * went by before giving their name (D-60). Ordered by name so repeated names stay together and
     * the list does not jump around between calls.
     */
    List<Patient> findByFullNameContainingIgnoreCaseOrDocumentNumberContainingOrAliasContainingIgnoreCaseOrderByFullNameAsc(
            String namePart, String documentNumberPart, String aliasPart);

    boolean existsByAlias(String alias);

    List<Patient> findAllByOrderByFullNameAsc();
}
