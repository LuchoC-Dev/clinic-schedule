package com.clinic.schedule.clinicalrecord.adapter.in.http;

import com.clinic.schedule.clinicalrecord.domain.AccessGrant;

import jakarta.validation.constraints.Size;

/**
 * The body of a request for access to report a check. Whether the reason may be left out is a
 * setting (D-54), answered by the use case, so it is not required here.
 */
record RequestAccessRequest(
        @Size(max = AccessGrant.MAX_REASON_LENGTH, message = "the reason is at most 500 characters")
        String reason) {
}
