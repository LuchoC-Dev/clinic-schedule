package com.clinic.schedule.arrival.session.http;

import java.util.UUID;

import com.clinic.schedule.arrival.session.query.ViewMyQueueUseCase.MyQueue;

/** The practitioner's own queue, as the API returns it: which one, its session, and whom they called (D-63). */
record MyQueueResponse(UUID queueId, String queueName, SessionResponse session, EntryResponse called) {

    static MyQueueResponse of(MyQueue mine) {
        return new MyQueueResponse(mine.queue().id(), mine.queue().name(),
                SessionResponse.of(mine.queue().id(), mine.session()),
                mine.called().map(EntryResponse::of).orElse(null));
    }
}
