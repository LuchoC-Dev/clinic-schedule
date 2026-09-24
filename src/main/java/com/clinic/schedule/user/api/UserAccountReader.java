package com.clinic.schedule.user.api;

import java.util.Optional;

/**
 * What other modules may ask about users. The security module asks it when someone logs in
 * (D-43).
 */
public interface UserAccountReader {

    /** The user with that name, if there is one and they may log in: an inactive user is as good as unknown. */
    Optional<UserAccount> findActiveByUsername(String username);
}
