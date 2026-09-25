package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link AccessGrantRow}; the application talks to {@code AccessGrantRepository}, the port. */
interface AccessGrantRowRepository extends JpaRepository<AccessGrantRow, UUID> {

    List<AccessGrantRow> findByPatientId(UUID patientId);

    List<AccessGrantRow> findByPatientIdAndPractitionerId(UUID patientId, UUID practitionerId);
}
