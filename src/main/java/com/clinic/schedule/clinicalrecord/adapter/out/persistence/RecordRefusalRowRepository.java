package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring Data over {@link RecordRefusalRow}; the application talks to {@code RecordRefusalWriter} and
 * {@code RecordRefusalReader}, the ports. Specifications, as for the readings (D-71).
 */
interface RecordRefusalRowRepository extends JpaRepository<RecordRefusalRow, UUID>,
        JpaSpecificationExecutor<RecordRefusalRow> {
}
