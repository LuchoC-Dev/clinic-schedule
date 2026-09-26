package com.clinic.schedule.patient.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.patient.entity.PatientIdentityChange;

/** The history of each patient's identity (D-60): only added to, and read on the patient's screen. */
public interface PatientIdentityChangeRepository extends JpaRepository<PatientIdentityChange, UUID> {

    List<PatientIdentityChange> findByPatientIdOrderByChangedAtDesc(UUID patientId);
}
