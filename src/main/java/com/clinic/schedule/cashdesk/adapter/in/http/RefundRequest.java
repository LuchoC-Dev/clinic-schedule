package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;

/** The pesos given back from a visit's charge, and why (D-88). */
record RefundRequest(BigDecimal amount, String reason) {
}
