package com.clinic.schedule.clinicalrecord.domain.exception;

import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;

/** Only whoever issued an order may cancel it (D-55). A 403. */
public class NotTheOrderAuthorException extends RuntimeException {

    public NotTheOrderAuthorException(CheckOrderId id) {
        super("Only whoever issued order " + id.value() + " may cancel it");
    }
}
