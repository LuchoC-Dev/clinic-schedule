package com.clinic.schedule.cashdesk.api;

/**
 * A visit going in covered by the patient's health insurance (D-90): the insurer was asked outside the
 * application and said yes. The cash desk keeps the patient's insurance as it is that day.
 *
 * @param token what the insurer's check gave for that visit; null when it asked for none
 */
public record Coverage(String token) {
}
