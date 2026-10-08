package com.clinic.schedule.cashdesk.adapter.in.http;

import java.math.BigDecimal;

import com.clinic.schedule.cashdesk.domain.Means;

/**
 * An emergency's pending visit recorded as covered by the patient's health insurance (D-90): the token the
 * insurer's check gave, if any, and the copay, if it asks for one, and how it was paid.
 *
 * @param means null for cash
 */
record CoverRequest(String token, BigDecimal copay, Means means) {
}
