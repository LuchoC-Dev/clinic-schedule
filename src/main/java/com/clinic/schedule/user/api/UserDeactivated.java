package com.clinic.schedule.user.api;

/**
 * A user was deactivated, published once it is saved (D-68). The security module ends their open
 * sessions: a deactivated user loses access at once, not when their session runs out.
 *
 * <p>An event rather than a call, because the security module already depends on this one's api,
 * and this one calling it back would close a cycle.
 */
public record UserDeactivated(String username) {
}
