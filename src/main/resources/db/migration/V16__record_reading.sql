-- Every reading of a patient's clinical record (D-59): who read it, whose it is, when, by which
-- access, and which attached file when that is what was read. Only ever inserted. Nothing reads
-- it yet, so it has no index beyond its key until something does.
--
-- practitioner_id and patient_id point at tables of other modules and have no foreign key (D-30);
-- the attachment is this module's own.
create table record_reading (
    id              uuid        primary key,
    practitioner_id uuid        not null,
    patient_id      uuid        not null,
    read_at         timestamp   not null,
    access          varchar(20) not null,
    attachment_id   uuid,
    constraint fk_record_reading_attachment foreign key (attachment_id) references attachment (id),
    constraint ck_record_reading_access check (access in ('VISIT_TODAY', 'ACCESS_GRANT', 'SEEN_WITHIN_PERIOD'))
);
