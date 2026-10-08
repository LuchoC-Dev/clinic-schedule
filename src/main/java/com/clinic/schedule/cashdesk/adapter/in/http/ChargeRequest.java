package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;

import com.clinic.schedule.cashdesk.domain.Means;

/**
 * The pesos charged for an emergency's pending visit (D-87), or a visit given back whole (D-88), and how they
 * were paid (D-89).
 *
 * @param means null for cash
 */
record ChargeRequest(BigDecimal amount, Means means) {
}
