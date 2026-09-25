package com.clinic.schedule.clinicalrecord.domain;

import java.util.UUID;

/** Identity of an order for a check, assigned when it is issued (D-19). */
public record CheckOrderId(UUID value) {

    public CheckOrderId {
        if (value == null) {
            throw new IllegalArgumentException("An order needs an id");
        }
    }

    public static CheckOrderId newId() {
        return new CheckOrderId(UUID.randomUUID());
    }
}
