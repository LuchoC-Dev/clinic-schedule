package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;

/** The pesos charged, in cash, for an emergency's pending visit (D-87). */
record ChargeRequest(BigDecimal amount) {
}
