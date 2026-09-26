package com.clinic.schedule.arrival.setup.http;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.schedule.arrival.setup.command.AddPractitionerUseCase;
import com.clinic.schedule.arrival.setup.command.CreateQueueUseCase;
import com.clinic.schedule.arrival.setup.command.RemovePractitionerUseCase;
import com.clinic.schedule.arrival.setup.command.RenameQueueUseCase;
import com.clinic.schedule.arrival.setup.domain.QueueId;
import com.clinic.schedule.arrival.setup.query.FindQueueUseCase;
import com.clinic.schedule.arrival.setup.query.ListQueuesUseCase;

/**
 * Setting up the queues, for reception (D-62). A command answers nothing of its own: each endpoint
 * runs it and then reads the queue back, so what comes back is what the reads see.
 *
 * <p>Taking a practitioner out of a queue is a {@code DELETE}: it removes them from the queue and
 * nothing else, and the queue's events still say they served it. Queues themselves are never
 * deleted.
 */
@RestController
@RequestMapping("/api/queues")
class QueueController {

    private final CreateQueueUseCase create;
    private final RenameQueueUseCase rename;
    private final AddPractitionerUseCase addPractitioner;
    private final RemovePractitionerUseCase removePractitioner;
    private final ListQueuesUseCase list;
    private final FindQueueUseCase find;

    QueueController(CreateQueueUseCase create, RenameQueueUseCase rename, AddPractitionerUseCase addPractitioner,
            RemovePractitionerUseCase removePractitioner, ListQueuesUseCase list, FindQueueUseCase find) {
        this.create = create;
        this.rename = rename;
        this.addPractitioner = addPractitioner;
        this.removePractitioner = removePractitioner;
        this.list = list;
        this.find = find;
    }

    @PostMapping
    ResponseEntity<QueueResponse> create(@Valid @RequestBody QueueRequest request) {
        QueueId id = create.handle(new CreateQueueUseCase.Command(request.name(), request.room()));
        return ResponseEntity.created(URI.create("/api/queues/" + id.value())).body(read(id));
    }

    @GetMapping
    List<QueueResponse> list() {
        return list.handle().stream().map(QueueResponse::of).toList();
    }

    @GetMapping("/{id}")
    QueueResponse byId(@PathVariable UUID id) {
        return read(new QueueId(id));
    }

    @PutMapping("/{id}")
    QueueResponse rename(@PathVariable UUID id, @Valid @RequestBody QueueRequest request) {
        rename.handle(new RenameQueueUseCase.Command(new QueueId(id), request.name(), request.room()));
        return read(new QueueId(id));
    }

    @PostMapping("/{id}/practitioners")
    QueueResponse addPractitioner(@PathVariable UUID id, @Valid @RequestBody AddPractitionerRequest request) {
        addPractitioner.handle(new AddPractitionerUseCase.Command(new QueueId(id), request.practitionerId()));
        return read(new QueueId(id));
    }

    @DeleteMapping("/{id}/practitioners/{practitionerId}")
    QueueResponse removePractitioner(@PathVariable UUID id, @PathVariable UUID practitionerId) {
        removePractitioner.handle(new RemovePractitionerUseCase.Command(new QueueId(id), practitionerId));
        return read(new QueueId(id));
    }

    private QueueResponse read(QueueId id) {
        return QueueResponse.of(find.handle(id));
    }
}
