package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link CheckOrderRow}; the application talks to {@code CheckOrderRepository}, the port. */
interface CheckOrderRowRepository extends JpaRepository<CheckOrderRow, UUID> {

    List<CheckOrderRow> findByPatientId(UUID patientId);
}
