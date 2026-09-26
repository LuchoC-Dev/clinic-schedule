-- The arrival module keeps events, not state (D-62). Each stream is the list of what happened to
-- one thing — a queue's setup today — in order; nothing in it is ever updated or deleted. Two
-- writers of the same stream at once take the same version, and the key refuses the second.
--
-- The payload is the event as JSON text; event_type is a fixed name, not a class name.
create table arrival_event (
    stream_id   uuid          not null,
    version     integer       not null,
    event_type  varchar(50)   not null,
    payload     varchar(2000) not null,
    recorded_at timestamp     not null,
    recorded_by varchar(50)   not null,
    constraint pk_arrival_event primary key (stream_id, version),
    constraint ck_arrival_event_version check (version > 0)
);

-- Projections: what the queue events add up to, written in the same transaction as the events.
-- They are what the screens list, and what guards the two rules that span queues: the name of a
-- queue is nobody else's, whatever the case, and a practitioner serves one queue at most. Being
-- derived, they could be rebuilt from arrival_event.
create table arrival_queue (
    id       uuid        primary key,
    name     varchar(60) not null,
    name_key varchar(60) not null,
    room     varchar(20),
    constraint uq_arrival_queue_name unique (name_key)
);

-- practitioner_id points at another module's table and has no foreign key (D-30).
create table queue_membership (
    practitioner_id uuid primary key,
    queue_id        uuid not null,
    constraint fk_queue_membership_queue foreign key (queue_id) references arrival_queue (id)
);

create index ix_queue_membership_queue on queue_membership (queue_id);
