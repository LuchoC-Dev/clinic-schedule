package com.clinic.schedule.portal.adapter.in.http;

import java.util.UUID;

import com.clinic.schedule.portal.domain.FamilyMember;

import jakarta.validation.constraints.NotNull;

/** The bodies reception sends to form a family group (D-84). */
final class FamilyGroupRequests {

    private FamilyGroupRequests() {
    }

    /** The group's rules check the name. */
    record Create(String name) {
    }

    /** Someone of 13 or older, present and agreeing; {@code shares} is an adolescent's choice. */
    record Join(@NotNull(message = "the patient is required") UUID patientId, boolean shares) {
    }

    /**
     * A child under 13, at the request of a parent or guardian in the group, once reception saw the proof. Without
     * the relation, the group's rules refuse it.
     */
    record AddChild(
            @NotNull(message = "the patient is required") UUID patientId,
            @NotNull(message = "who asked for it is required") UUID requestedBy,
            FamilyMember.Relation relation,
            boolean proofSeen) {
    }

    record Share(boolean shares) {
    }
}
