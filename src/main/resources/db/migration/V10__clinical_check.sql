-- Checks: done to a patient during a consultation or, as a precaution, without one; their result
-- arrives with them or later, maybe written by someone else (D-53). The table is clinical_check
-- because check is a reserved word in SQL.
--
-- patient_id, performed_by and result_author point at tables of other modules and have no foreign
-- key (D-30); consultation_id points at this module's own table, so it has one.
create table clinical_check (
    id                uuid           primary key,
    version           bigint         default 0 not null,
    patient_id        uuid           not null,
    performed_by      uuid           not null,
    performed_at      timestamp      not null,
    description       varchar(2000)  not null,
    consultation_id   uuid,
    result_author     uuid,
    result_written_at timestamp,
    result_text       varchar(10000),
    constraint fk_clinical_check_consultation foreign key (consultation_id) references consultation (id),
    -- A result is whole or absent: its author, its time and its text together.
    constraint ck_clinical_check_result check (
        (result_author is null and result_written_at is null and result_text is null)
        or (result_author is not null and result_written_at is not null and result_text is not null))
);

-- The two reads: a patient's record, and a practitioner's last check of a patient (D-49, D-53).
create index ix_clinical_check_patient on clinical_check (patient_id, performed_at);
create index ix_clinical_check_patient_performer on clinical_check (patient_id, performed_by, performed_at);

-- A check is not an item of a consultation any more. The database starts empty for this version,
-- so no item of that kind is carried over (D-53).
alter table consultation_item drop constraint ck_consultation_item_kind;
alter table consultation_item add constraint ck_consultation_item_kind check (kind in ('PRESCRIPTION', 'STUDY_ORDER'));
