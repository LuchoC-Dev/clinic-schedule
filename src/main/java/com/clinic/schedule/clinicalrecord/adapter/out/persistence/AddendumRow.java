package com.clinic.schedule.clinicalrecord.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.clinic.schedule.clinicalrecord.domain.Addendum;
import com.clinic.schedule.clinicalrecord.domain.AddendumId;
import com.clinic.schedule.clinicalrecord.domain.PractitionerId;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One addendum of a consultation as a row of {@code consultation_addendum}. */
@Embeddable
class AddendumRow {

    @Column(nullable = false)
    private UUID id;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "written_at", nullable = false)
    private LocalDateTime writtenAt;

    @Column(nullable = false)
    private String text;

    protected AddendumRow() {
        // required by JPA
    }

    static AddendumRow from(Addendum addendum) {
        AddendumRow row = new AddendumRow();
        row.id = addendum.id().value();
        row.practitionerId = addendum.author().value();
        row.writtenAt = addendum.writtenAt();
        row.text = addendum.text();
        return row;
    }

    Addendum toAddendum() {
        return new Addendum(new AddendumId(id), new PractitionerId(practitionerId), writtenAt, text);
    }
}
