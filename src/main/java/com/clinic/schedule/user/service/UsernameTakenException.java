package com.clinic.schedule.user.service;

/** The username is, or was, someone else's: a username is never used twice (D-68). */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("The username " + username + " is taken, now or before");
    }
}
