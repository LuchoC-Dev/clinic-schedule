package com.clinic.schedule.portal.domain.exception;

/** Something asked of a web account that its state does not allow, such as deactivating one already off. */
public class AccountStateException extends RuntimeException {

    public AccountStateException(String message) {
        super(message);
    }
}
