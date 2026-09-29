package com.clinic.schedule.patient.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinic.schedule.patient.entity.PatientDetailsChange;

/**
 * The history of each patient's details (D-73): only added to, and read by reception. The entries of
 * one save share their time, and come in the order of the details.
 */
public interface PatientDetailsChangeRepository extends JpaRepository<PatientDetailsChange, UUID> {

    List<PatientDetailsChange> findByPatientIdOrderByChangedAtDescFieldAsc(UUID patientId);
}
