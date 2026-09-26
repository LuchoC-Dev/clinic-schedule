-- Queue sessions (D-63): each opening of a queue is a stream in arrival_event, from its opening to
-- its closing. These tables are its projection, written in the same transaction as the events:
-- what the screens show and what the clinical record asks, and what guards the one rule that spans
-- sessions — a queue has one open session at a time.

create table queue_session (
    id         uuid      primary key,
    queue_id   uuid      not null,
    opened_at  timestamp not null,
    closed_at  timestamp,
    auto_close boolean   not null,
    calls      integer   not null,
    constraint fk_queue_session_queue foreign key (queue_id) references arrival_queue (id)
);

create index ix_queue_session_queue on queue_session (queue_id);

-- A row while the session is open; its key refuses a second one for the same queue.
create table open_queue_session (
    queue_id   uuid primary key,
    session_id uuid not null,
    constraint uq_open_queue_session_session unique (session_id),
    constraint fk_open_queue_session_queue foreign key (queue_id) references arrival_queue (id),
    constraint fk_open_queue_session_session foreign key (session_id) references queue_session (id)
);

-- Each patient's place in a session. patient_id and with_practitioner point at tables of other
-- modules and have no foreign key (D-30).
create table session_entry (
    id                  uuid        primary key,
    session_id          uuid        not null,
    sequence            integer     not null,
    patient_id          uuid        not null,
    arrived_at          timestamp   not null,
    emergency_since     timestamp,
    state               varchar(20) not null,
    times_called        integer     not null,
    with_practitioner   uuid,
    last_called_at      timestamp,
    passed_over_at_call integer     not null,
    constraint fk_session_entry_session foreign key (session_id) references queue_session (id),
    constraint ck_session_entry_state check (
        state in ('WAITING', 'CALLED', 'SEEN', 'LEFT', 'NOT_SEEN', 'DID_NOT_SHOW_UP')
    )
);

create index ix_session_entry_session on session_entry (session_id);
-- The clinical record asks where a patient is today.
create index ix_session_entry_patient on session_entry (patient_id);
