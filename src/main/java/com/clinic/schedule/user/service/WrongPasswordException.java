package com.clinic.schedule.user.service;

/** The current password given to change it is not the one the user has (D-69). */
public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("The current password is wrong");
    }
}
