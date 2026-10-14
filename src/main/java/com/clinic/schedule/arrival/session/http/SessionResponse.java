package com.clinic.schedule.arrival.session.http;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import com.clinic.schedule.arrival.session.domain.Entry;
import com.clinic.schedule.arrival.session.query.SessionView;
import com.clinic.schedule.patient.api.PatientReader;
import com.clinic.schedule.practitioner.api.PractitionerReader;

/**
 * A queue's current session as the API returns it (D-63): whether it is open and, when it is, who
 * waits in the order they will be called, who of them nobody may call, who was called, and who is done.
 */
record SessionResponse(UUID queueId, boolean open, UUID sessionId, LocalDateTime openedAt, Boolean closesByItself,
        List<EntryResponse> waiting, List<EntryResponse> uncallable, List<EntryResponse> called,
        List<EntryResponse> done) {

    static SessionResponse of(UUID queueId, Optional<SessionView> session, PatientReader patients,
            PractitionerReader practitioners) {
        return session.map(open -> {
                    Names names = Names.of(Stream.of(open.waiting(), open.uncallable(), open.called(), open.done())
                            .flatMap(List::stream).toList(), patients, practitioners);
                    Function<List<Entry>, List<EntryResponse>> entries =
                            list -> list.stream().map(entry -> EntryResponse.of(entry, names)).toList();
                    return new SessionResponse(queueId, true, open.sessionId(), open.openedAt(),
                            open.closesByItself(), entries.apply(open.waiting()), entries.apply(open.uncallable()),
                            entries.apply(open.called()), entries.apply(open.done()));
                })
                .orElseGet(() -> new SessionResponse(queueId, false, null, null, null, List.of(), List.of(), List.of(), List.of()));
    }
}
