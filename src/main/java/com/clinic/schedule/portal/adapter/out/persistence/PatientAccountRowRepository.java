package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PatientAccountRowRepository extends JpaRepository<PatientAccountRow, UUID> {

    Optional<PatientAccountRow> findByPatientId(UUID patientId);
}
