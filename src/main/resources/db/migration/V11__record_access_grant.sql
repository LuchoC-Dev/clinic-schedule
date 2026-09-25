-- Access to a patient's clinical record granted to a practitioner who asked for it to report a
-- pending check (D-54). Written once and kept, valid or not, as the record of who opened whose
-- history and why.
--
-- patient_id and practitioner_id point at tables of other modules and have no foreign key (D-30);
-- check_id points at this module's own table, so it has one.
create table record_access_grant (
    id              uuid         primary key,
    patient_id      uuid         not null,
    practitioner_id uuid         not null,
    check_id        uuid         not null,
    granted_at      timestamp    not null,
    valid_until     date         not null,
    reason          varchar(500),
    approval        varchar(20)  not null,
    constraint fk_record_access_grant_check foreign key (check_id) references clinical_check (id),
    constraint ck_record_access_grant_approval check (approval in ('AUTOMATIC'))
);

-- The two reads: a practitioner's grants for a patient, and every grant shown in a patient's record.
create index ix_record_access_grant_patient on record_access_grant (patient_id, practitioner_id);
