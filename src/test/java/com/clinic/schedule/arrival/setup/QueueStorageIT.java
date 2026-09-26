package com.clinic.schedule.arrival.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.clinic.schedule.arrival.eventstore.EventStore;
import com.clinic.schedule.arrival.eventstore.NewEvent;
import com.clinic.schedule.arrival.eventstore.StreamChangedException;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerJoined;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueCreated;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.domain.exception.PractitionerInAnotherQueueException;
import com.clinic.schedule.arrival.setup.domain.exception.QueueNameTakenException;
import com.clinic.schedule.arrival.setup.projection.QueueProjection;

/**
 * What the database guards when two writers get past the rules at once (D-62): a stream refuses a
 * stale version, the projection refuses a taken name or a practitioner in two queues, and a refused
 * projection takes the events of its transaction with it.
 */
@SpringBootTest
class QueueStorageIT {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 0, 0);

    @Autowired
    private EventStore events;

    @Autowired
    private QueueProjection projection;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    private static NewEvent created() {
        return new NewEvent("QueueCreated", "{\"name\":\"x\",\"room\":null}");
    }

    private int eventsOf(UUID stream) {
        return jdbc.queryForObject("select count(*) from arrival_event where stream_id = ?", Integer.class, stream);
    }

    @Test
    void aStreamRefusesAWriterWhoReadItBeforeTheLastChange() {
        UUID stream = UUID.randomUUID();
        events.append(stream, 0, List.of(created()), NOW, "recepcion");

        assertThatThrownBy(() -> events.append(stream, 0, List.of(created()), NOW, "recepcion"))
                .isInstanceOf(StreamChangedException.class);
        assertThat(events.load(stream)).hasSize(1);
    }

    @Test
    void theProjectionRefusesATakenNameAndAPractitionerInTwoQueues() {
        String name = "Cola " + UUID.randomUUID();
        QueueId first = QueueId.newId();
        QueueId second = QueueId.newId();
        UUID practitioner = UUID.randomUUID();
        projection.apply(first, List.of(new QueueCreated(name, null), new PractitionerJoined(practitioner)));

        assertThatThrownBy(() -> projection.apply(second, List.of(new QueueCreated(name.toLowerCase(), null))))
                .isInstanceOf(QueueNameTakenException.class);
        projection.apply(second, List.of(new QueueCreated("Otra " + name, null)));
        assertThatThrownBy(() -> projection.apply(second, List.of(new PractitionerJoined(practitioner))))
                .isInstanceOf(PractitionerInAnotherQueueException.class);
    }

    @Test
    void aRefusedProjectionTakesItsEventsWithIt() {
        String name = "Cola " + UUID.randomUUID();
        projection.apply(QueueId.newId(), List.of(new QueueCreated(name, null)));
        QueueId racing = QueueId.newId();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            events.append(racing.value(), 0, List.of(created()), NOW, "recepcion");
            projection.apply(racing, List.of(new QueueCreated(name, null)));
        })).isInstanceOf(QueueNameTakenException.class);

        assertThat(eventsOf(racing.value())).isZero();
    }
}
