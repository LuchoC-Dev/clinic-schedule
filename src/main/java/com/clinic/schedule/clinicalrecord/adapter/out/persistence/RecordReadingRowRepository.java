package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data over {@link RecordReadingRow}; the application talks to {@code RecordReadingWriter}, the port. */
interface RecordReadingRowRepository extends JpaRepository<RecordReadingRow, UUID> {
}
