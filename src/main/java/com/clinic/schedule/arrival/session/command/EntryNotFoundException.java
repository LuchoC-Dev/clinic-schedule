package com.clinic.schedule.arrival.session.command;

import com.clinic.schedule.arrival.session.domain.EntryId;

/** No session has that entry. Maps to 404. */
public class EntryNotFoundException extends RuntimeException {

    EntryNotFoundException(EntryId id) {
        super("No patient in a queue with entry id " + id.value());
    }
}
