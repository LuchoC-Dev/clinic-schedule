package com.clinic.schedule.portal.application.port;

import java.util.Optional;

import com.clinic.schedule.portal.domain.CancelLink;

/** Where the links to cancel appointments are kept, as hashes (D-83). */
public interface CancelLinkRepository {

    void save(CancelLink link);

    Optional<CancelLink> findByHash(String hash);
}
