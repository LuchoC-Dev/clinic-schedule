-- Addenda: notes added to a consultation after it was sent, with their author and time. They
-- are only ever added; nothing already written changes (D-52).
--
-- practitioner_id has no foreign key: it points at a table of another module (D-30).
create table consultation_addendum (
    consultation_id uuid          not null,
    position        integer       not null,
    id              uuid          not null,
    practitioner_id uuid          not null,
    written_at      timestamp     not null,
    text            varchar(2000) not null,
    constraint pk_consultation_addendum primary key (consultation_id, position),
    constraint fk_consultation_addendum_consultation foreign key (consultation_id) references consultation (id)
);

-- The version that stops two addenda written at the same time from overwriting each other.
alter table consultation add column version bigint default 0 not null;
