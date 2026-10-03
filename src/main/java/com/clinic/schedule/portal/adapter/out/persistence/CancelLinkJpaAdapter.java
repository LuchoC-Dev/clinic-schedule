package com.clinic.schedule.portal.adapter.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.clinic.schedule.portal.application.port.CancelLinkRepository;
import com.clinic.schedule.portal.domain.CancelLink;

/** Implements the port over JPA. */
@Repository
class CancelLinkJpaAdapter implements CancelLinkRepository {

    private final CancelLinkRowRepository rows;

    CancelLinkJpaAdapter(CancelLinkRowRepository rows) {
        this.rows = rows;
    }

    @Override
    public void save(CancelLink link) {
        rows.save(CancelLinkRow.from(link));
    }

    @Override
    public Optional<CancelLink> findByHash(String hash) {
        return rows.findById(hash).map(CancelLinkRow::toLink);
    }
}
