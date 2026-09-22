-- Appointments. Ids are uuid because they are assigned when the appointment is created and
-- not by the database (D-19); both H2 and PostgreSQL support the type natively.
--
-- patient_id and practitioner_id have no foreign key yet: those modules do not exist. The
-- keys are added by the migrations that create their tables.
--
-- The slot is stored as a start plus a duration in minutes, mirroring the domain. There is
-- no stored end time, which is why overlap is decided in the domain over a day's worth of
-- appointments instead of in SQL (D-20).
create table appointment (
    id               uuid         primary key,
    patient_id       uuid         not null,
    practitioner_id  uuid         not null,
    starts_at        timestamp    not null,
    duration_minutes integer      not null,
    reason           varchar(100) not null,
    note             varchar(500),
    status           varchar(20)  not null,
    constraint ck_appointment_status check (status in ('BOOKED', 'CANCELLED')),
    constraint ck_appointment_duration check (duration_minutes > 0)
);

-- The two reads the application makes: a practitioner's day and a patient's day.
create index ix_appointment_practitioner_day on appointment (practitioner_id, starts_at);
create index ix_appointment_patient_day on appointment (patient_id, starts_at);
