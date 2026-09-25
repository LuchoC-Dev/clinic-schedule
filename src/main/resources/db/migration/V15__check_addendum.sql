-- Addenda to a check, pending or reported: how a wrong result is corrected, since a result never
-- changes (D-58). Like the addenda of a consultation (V9), they are only ever added, in order.
--
-- practitioner_id has no foreign key: it points at a table of another module (D-30).
create table check_addendum (
    check_id        uuid          not null,
    position        integer       not null,
    id              uuid          not null,
    practitioner_id uuid          not null,
    written_at      timestamp     not null,
    text            varchar(2000) not null,
    constraint pk_check_addendum primary key (check_id, position),
    constraint fk_check_addendum_check foreign key (check_id) references clinical_check (id)
);
