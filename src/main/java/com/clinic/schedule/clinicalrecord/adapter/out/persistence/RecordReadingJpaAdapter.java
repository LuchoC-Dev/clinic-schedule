package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.port.RecordReadingWriter;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;

/** Implements the port over JPA. The only place that knows readings live in a table. */
@Repository
class RecordReadingJpaAdapter implements RecordReadingWriter {

    private final RecordReadingRowRepository rows;

    RecordReadingJpaAdapter(RecordReadingRowRepository rows) {
        this.rows = rows;
    }

    /** Only ever a new reading: nothing changes one once it is recorded. */
    @Override
    public void save(RecordReading reading) {
        rows.save(RecordReadingRow.from(reading));
    }
}
