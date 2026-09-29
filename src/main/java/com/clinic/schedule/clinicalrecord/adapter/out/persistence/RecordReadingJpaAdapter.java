package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.clinic.schedule.clinicalrecord.application.port.RecordReadingReader;
import com.clinic.schedule.clinicalrecord.application.port.RecordReadingWriter;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;
import com.clinic.schedule.clinicalrecord.domain.RecordReading;

/** Implements the ports over JPA. The only place that knows readings live in a table. */
@Repository
class RecordReadingJpaAdapter implements RecordReadingWriter, RecordReadingReader {

    private final RecordReadingRowRepository rows;

    RecordReadingJpaAdapter(RecordReadingRowRepository rows) {
        this.rows = rows;
    }

    /** Only ever a new reading: nothing changes one once it is recorded. */
    @Override
    public void save(RecordReading reading) {
        rows.save(RecordReadingRow.from(reading));
    }

    @Override
    public List<RecordReading> find(Set<PatientId> patients, PractitionerId practitioner, LocalDateTime from,
            LocalDateTime to, int limit) {
        if (patients != null && patients.isEmpty()) {
            return List.of();
        }
        Specification<RecordReadingRow> search = (row, query, criteria) -> criteria.and(
                criteria.greaterThanOrEqualTo(row.get("readAt"), from),
                criteria.lessThan(row.get("readAt"), to));
        if (patients != null) {
            search = search.and((row, query, criteria) -> row.get("patientId")
                    .in(patients.stream().map(PatientId::value).toList()));
        }
        if (practitioner != null) {
            search = search.and((row, query, criteria) -> criteria.equal(row.get("practitionerId"), practitioner.value()));
        }
        return rows.findBy(search, found -> found.sortBy(Sort.by(Sort.Direction.DESC, "readAt")).limit(limit).all())
                .stream()
                .map(RecordReadingRow::toDomain)
                .toList();
    }
}
