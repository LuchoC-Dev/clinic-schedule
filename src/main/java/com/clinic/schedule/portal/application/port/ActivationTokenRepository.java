package com.clinic.schedule.portal.application.port;

import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.portal.domain.ActivationToken;

/** Where the pending activation of each account is kept: one at most, as a hash of its token. */
public interface ActivationTokenRepository {

    /** Replaces the account's activation, if it had one: the last link sent is the only one that works. */
    void save(ActivationToken token);

    Optional<ActivationToken> findByHash(String hash);

    /** The account's activation ends, used or no longer wanted. */
    void deleteForAccount(UUID accountId);
}
