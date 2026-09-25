package com.clinic.schedule.clinicalrecord.application.exception;

import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;

/** A check was said to fulfil an order that is not one of the patient's (D-55). A 400. */
public class UnknownOrderException extends RuntimeException {

    public UnknownOrderException(CheckOrderId id) {
        super("Order " + id.value() + " is not one of the patient's orders");
    }
}
