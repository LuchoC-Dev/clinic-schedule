-- Every refused attempt to reach clinical content (D-72): a practitioner refused a record or an
-- attached file, or a user refused a clinical route by their role. Only ever inserted; the admins
-- read them.
--
-- practitioner_id and patient_id point at tables of other modules and have no foreign key (D-30); the
-- attachment has none either, since the file asked for may not exist. Each is null when unknown: the
-- user stands for no practitioner, the route names no patient, the file does not exist.
create table record_refusal (
    id              uuid         primary key,
    refused_by      varchar(50)  not null,
    practitioner_id uuid,
    refused_at      timestamp    not null,
    attempt         varchar(12)  not null,
    patient_id      uuid,
    attachment_id   uuid,
    route           varchar(300),
    constraint ck_record_refusal_attempt check (attempt in ('RECORD', 'ATTACHMENT', 'ROUTE')),
    constraint ck_record_refusal_route check (
        (attempt = 'ROUTE' and route is not null) or (attempt <> 'ROUTE' and route is null)
    )
);

-- What the admins search by, as for the readings.
create index ix_record_refusal_refused_at on record_refusal (refused_at);
create index ix_record_refusal_patient on record_refusal (patient_id, refused_at);
