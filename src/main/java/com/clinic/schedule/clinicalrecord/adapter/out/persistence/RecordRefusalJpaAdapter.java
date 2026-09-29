package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.schedule.clinicalrecord.application.port.RecordRefusalReader;
import com.clinic.schedule.clinicalrecord.application.port.RecordRefusalWriter;
import com.clinic.schedule.clinicalrecord.domain.PatientId;
import com.clinic.schedule.clinicalrecord.domain.RecordRefusal;

/** Implements the ports over JPA. The only place that knows refusals live in a table. */
@Repository
class RecordRefusalJpaAdapter implements RecordRefusalWriter, RecordRefusalReader {

    private final RecordRefusalRowRepository rows;

    RecordRefusalJpaAdapter(RecordRefusalRowRepository rows) {
        this.rows = rows;
    }

    /**
     * In a transaction of its own: the use case that refuses throws, which rolls its own back, and the
     * refusal must stay (D-72).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(RecordRefusal refusal) {
        rows.save(RecordRefusalRow.from(refusal));
    }

    @Override
    public List<RecordRefusal> find(Set<PatientId> patients, String refusedBy, LocalDateTime from, LocalDateTime to,
            int limit) {
        if (patients != null && patients.isEmpty()) {
            return List.of();
        }
        Specification<RecordRefusalRow> search = (row, query, criteria) -> criteria.and(
                criteria.greaterThanOrEqualTo(row.get("refusedAt"), from),
                criteria.lessThan(row.get("refusedAt"), to));
        if (patients != null) {
            search = search.and((row, query, criteria) -> row.get("patientId")
                    .in(patients.stream().map(PatientId::value).toList()));
        }
        if (refusedBy != null) {
            search = search.and((row, query, criteria) -> criteria.equal(row.get("refusedBy"), refusedBy));
        }
        return rows.findBy(search, found -> found.sortBy(Sort.by(Sort.Direction.DESC, "refusedAt")).limit(limit).all())
                .stream()
                .map(RecordRefusalRow::toDomain)
                .toList();
    }
}
