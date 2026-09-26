package com.clinic.schedule.arrival.setup.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerJoined;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.PractitionerLeft;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueCreated;
import com.clinic.schedule.arrival.setup.domain.QueueEvent.QueueRenamed;
import com.clinic.schedule.arrival.setup.domain.exception.PractitionerInAnotherQueueException;
import com.clinic.schedule.arrival.setup.domain.exception.QueueNameTakenException;

/**
 * The only way to change how queues are set up (D-17, D-62), because two rules look beyond one
 * queue:
 *
 * <ul>
 *   <li>no two queues share a name, whatever the case;</li>
 *   <li>a practitioner serves one queue at most.</li>
 * </ul>
 *
 * <p>It is given what it needs to know about the other queues and reads nothing: fetching it is
 * the command's job. Each decision answers with the event that records it. The database guards
 * both rules too, so two commands racing past this check still cannot break them.
 */
public class QueueSetupService {

    /** @param takenNames the names of every other queue */
    public QueueCreated create(String name, String room, Collection<String> takenNames) {
        QueueCreated created = Queue.create(name, room);
        requireFree(created.name(), takenNames);
        return created;
    }

    /** @param takenNames the names of every queue but this one */
    public QueueRenamed rename(Queue queue, String name, String room, Collection<String> takenNames) {
        QueueRenamed renamed = queue.rename(name, room);
        requireFree(renamed.name(), takenNames);
        return renamed;
    }

    /** @param servedQueue the queue the practitioner serves now, if any */
    public PractitionerJoined join(Queue queue, UUID practitionerId, Optional<QueueId> servedQueue) {
        PractitionerJoined joined = queue.join(practitionerId);
        if (servedQueue.isPresent() && !servedQueue.get().equals(queue.id())) {
            throw new PractitionerInAnotherQueueException();
        }
        return joined;
    }

    public PractitionerLeft leave(Queue queue, UUID practitionerId) {
        return queue.leave(practitionerId);
    }

    private static void requireFree(String name, Collection<String> takenNames) {
        if (takenNames.stream().anyMatch(name::equalsIgnoreCase)) {
            throw new QueueNameTakenException(name);
        }
    }
}
