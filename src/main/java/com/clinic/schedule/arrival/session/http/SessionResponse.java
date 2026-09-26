package com.clinic.schedule.arrival.session.http;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.arrival.session.query.SessionView;

/**
 * A queue's current session as the API returns it (D-63): whether it is open and, when it is, who
 * waits in the order they will be called, who was called, and who is done.
 */
record SessionResponse(UUID queueId, boolean open, UUID sessionId, LocalDateTime openedAt, Boolean closesByItself,
        List<EntryResponse> waiting, List<EntryResponse> called, List<EntryResponse> done) {

    static SessionResponse of(UUID queueId, Optional<SessionView> session) {
        return session.map(open -> new SessionResponse(queueId, true, open.sessionId(), open.openedAt(),
                        open.closesByItself(), open.waiting().stream().map(EntryResponse::of).toList(),
                        open.called().stream().map(EntryResponse::of).toList(),
                        open.done().stream().map(EntryResponse::of).toList()))
                .orElseGet(() -> new SessionResponse(queueId, false, null, null, null, List.of(), List.of(), List.of()));
    }
}
