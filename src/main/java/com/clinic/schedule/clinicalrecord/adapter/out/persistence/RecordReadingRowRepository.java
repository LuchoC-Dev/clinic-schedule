package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring Data over {@link RecordReadingRow}; the application talks to {@code RecordReadingWriter} and
 * {@code RecordReadingReader}, the ports. Specifications, because each filter of the admins' search
 * applies only when it is given.
 */
interface RecordReadingRowRepository extends JpaRepository<RecordReadingRow, UUID>,
        JpaSpecificationExecutor<RecordReadingRow> {
}
