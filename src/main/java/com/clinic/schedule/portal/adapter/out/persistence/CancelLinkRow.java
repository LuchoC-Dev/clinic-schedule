package com.clinic.schedule.portal.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.portal.domain.CancelLink;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A link to cancel an appointment as a row (D-83), keyed by the hash of its token. Only ever inserted. */
@Entity
@Table(name = "cancel_link")
class CancelLinkRow {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    protected CancelLinkRow() {
        // required by JPA
    }

    static CancelLinkRow from(CancelLink link) {
        CancelLinkRow row = new CancelLinkRow();
        row.tokenHash = link.hash();
        row.appointmentId = link.appointmentId();
        row.startsAt = link.startsAt();
        return row;
    }

    CancelLink toLink() {
        return new CancelLink(tokenHash, appointmentId, startsAt);
    }
}
