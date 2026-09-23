-- A practitioner's time off: a whole day, or part of one. Stored as a start and an exclusive
-- end, like every other stretch of the agenda. Reception records it; the appointments it
-- affects are handled by the appointment module in the same transaction (D-32).
create table time_off (
    practitioner_id uuid      not null,
    starts_at       timestamp not null,
    ends_at         timestamp not null,
    constraint fk_time_off_practitioner foreign key (practitioner_id) references practitioner (id),
    constraint ck_time_off_range check (starts_at < ends_at)
);

create index ix_time_off_practitioner on time_off (practitioner_id);
