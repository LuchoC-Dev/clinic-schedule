package com.clinic.schedule.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByNationalId(String nationalId);

    boolean existsByNationalId(String nationalId);

    /**
     * One search box for reception: part of a name, or the national ID. Ordered by name so
     * repeated names stay together and the list does not jump around between calls.
     */
    List<Patient> findByFullNameContainingIgnoreCaseOrNationalIdContainingOrderByFullNameAsc(
            String namePart, String nationalIdPart);

    List<Patient> findAllByOrderByFullNameAsc();
}
