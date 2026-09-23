-- Practitioners, the reasons an appointment can be requested for, and their weekly hours.
-- Reception configures all of it; a practitioner asks reception for any change.

create table practitioner (
    id        uuid         primary key,
    full_name varchar(150) not null
);

-- Each practitioner has their own short list of reasons, each with a fixed duration for that
-- practitioner. A reason has its own id so it can be renamed without losing its identity.
create table reason (
    id               uuid        primary key,
    practitioner_id  uuid        not null,
    name             varchar(60) not null,
    duration_minutes integer     not null,
    constraint fk_reason_practitioner foreign key (practitioner_id) references practitioner (id),
    constraint uq_reason_name unique (practitioner_id, name),
    constraint ck_reason_duration check (duration_minutes > 0)
);

-- The usual week: one row per stretch of work on a weekday. A break is the gap between two
-- stretches of the same day, so Monday 09:00-13:00 plus 14:00-18:00 is a working day with a
-- lunch break. There is no separate table for breaks (D-28).
create table working_hours (
    practitioner_id uuid        not null,
    day_of_week     varchar(10) not null,
    starts_at       time        not null,
    ends_at         time        not null,
    constraint fk_working_hours_practitioner foreign key (practitioner_id) references practitioner (id),
    constraint ck_working_hours_day check (day_of_week in
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    constraint ck_working_hours_range check (starts_at < ends_at)
);

create index ix_working_hours_practitioner on working_hours (practitioner_id);
