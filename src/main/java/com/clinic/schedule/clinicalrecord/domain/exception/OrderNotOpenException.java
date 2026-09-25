package com.clinic.schedule.clinicalrecord.domain.exception;

import com.clinic.schedule.clinicalrecord.domain.CheckOrderId;

/**
 * The order was already fulfilled or cancelled — maybe a moment ago, by someone else. Only an open
 * order is fulfilled or cancelled (D-55). A 409.
 */
public class OrderNotOpenException extends RuntimeException {

    public OrderNotOpenException(CheckOrderId id) {
        super("Order " + id.value() + " is not open any more");
    }
}
