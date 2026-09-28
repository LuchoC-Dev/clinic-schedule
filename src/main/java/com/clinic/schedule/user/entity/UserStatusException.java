package com.clinic.schedule.user.entity;

/** A user deactivated when inactive already, or reactivated when active already. */
public class UserStatusException extends RuntimeException {

    public UserStatusException(String message) {
        super(message);
    }
}
