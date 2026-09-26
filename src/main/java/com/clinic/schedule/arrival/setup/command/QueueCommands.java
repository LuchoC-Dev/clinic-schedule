package com.clinic.schedule.arrival.setup.command;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.clinic.schedule.arrival.setup.QueueNotFoundException;
import com.clinic.schedule.arrival.setup.domain.Queue;
import com.clinic.schedule.arrival.setup.domain.QueueEvent;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.projection.QueueProjection;
import com.clinic.schedule.security.api.CurrentUserReader;

/**
 * What every command on a queue's setup does after deciding: add the events to the queue's stream,
 * stamped with the practice's clock and the user, and bring the projection up to date — in the
 * caller's transaction, so both are kept or neither (D-62). Package-private: a detail of the
 * commands, which own the transaction.
 */
@Component
class QueueCommands {

    private final QueueEventRepository events;
    private final QueueProjection projection;
    private final CurrentUserReader currentUser;
    private final Clock clock;

    QueueCommands(QueueEventRepository events, QueueProjection projection, CurrentUserReader currentUser,
            Clock clock) {
        this.events = events;
        this.projection = projection;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    Queue find(QueueId id) {
        return events.find(id).orElseThrow(() -> new QueueNotFoundException(id));
    }

    void record(QueueId id, int expectedVersion, QueueEvent event) {
        List<QueueEvent> recorded = List.of(event);
        events.append(id, expectedVersion, recorded, LocalDateTime.now(clock), currentUser.username());
        projection.apply(id, recorded);
    }
}
