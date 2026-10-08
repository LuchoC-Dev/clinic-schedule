package com.clinic.schedule.arrival.session.http;

import java.math.BigDecimal;

import com.clinic.schedule.cashdesk.api.Means;

/**
 * Whether a patient who gives no name comes in as an emergency, and the pesos charged, which only an
 * emergency may leave out (D-87), and how they were paid — left out, in cash (D-89). An emergency may leave the
 * body out.
 */
record AddUnidentifiedRequest(Boolean emergency, BigDecimal amount, Means means) {

    boolean isEmergency() {
        return Boolean.TRUE.equals(emergency);
    }
}
