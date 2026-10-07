package com.clinic.schedule.arrival.session.http;

import java.math.BigDecimal;

/**
 * Whether a patient who gives no name comes in as an emergency, and the pesos charged, which only an
 * emergency may leave out (D-87). An emergency may leave the body out.
 */
record AddUnidentifiedRequest(Boolean emergency, BigDecimal amount) {

    boolean isEmergency() {
        return Boolean.TRUE.equals(emergency);
    }
}
