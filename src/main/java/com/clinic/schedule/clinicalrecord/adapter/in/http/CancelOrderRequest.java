package com.clinic.schedule.clinicalrecord.adapter.in.http;

import com.clinic.schedule.clinicalrecord.domain.OrderOutcome;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of an order's cancellation: an order is cancelled with a reason (D-55). */
record CancelOrderRequest(
        @NotBlank(message = "the reason is required")
        @Size(max = OrderOutcome.Cancelled.MAX_REASON_LENGTH, message = "the reason is at most 500 characters")
        String reason) {
}
