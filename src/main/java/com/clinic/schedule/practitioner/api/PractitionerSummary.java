package com.clinic.schedule.practitioner.api;

import java.util.List;
import java.util.UUID;

/** A practitioner as other modules see it: who they are and what can be booked with them. */
public record PractitionerSummary(UUID id, String fullName, List<ReasonDetails> reasons) {
}
