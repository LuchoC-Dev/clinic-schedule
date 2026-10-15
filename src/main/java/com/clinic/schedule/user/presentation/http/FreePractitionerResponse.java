package com.clinic.schedule.user.presentation.http;

import java.util.UUID;

/** A practitioner with no active user, whom a new practitioner's user may stand for (D-68). */
record FreePractitionerResponse(UUID id, String fullName) {
}
