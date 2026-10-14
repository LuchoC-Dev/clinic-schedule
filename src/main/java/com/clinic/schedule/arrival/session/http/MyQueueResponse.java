package com.clinic.schedule.arrival.session.http;

import java.util.List;

import java.util.UUID;

import com.clinic.schedule.arrival.session.query.ViewMyQueueUseCase.MyQueue;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.practitioner.api.PractitionerReader;

/** The practitioner's own queue, as the API returns it: which one, its session, and whom they called (D-63). */
record MyQueueResponse(UUID queueId, String queueName, SessionResponse session, EntryResponse called) {

    static MyQueueResponse of(MyQueue mine, PatientReader patients, PractitionerReader practitioners) {
        return new MyQueueResponse(mine.queue().id(), mine.queue().name(),
                SessionResponse.of(mine.queue().id(), mine.session(), patients, practitioners),
                mine.called().map(entry -> EntryResponse.of(entry,
                        Names.of(List.of(entry), patients, practitioners))).orElse(null));
    }
}
