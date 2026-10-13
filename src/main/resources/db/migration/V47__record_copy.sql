-- A copy of a patient's whole clinical record, asked for from a web account (D-95): who asked, for whom, when,
-- how it is delivered, its code, when the record was copied as it stood, who authenticated it and when, and, on
-- paper, when reception handed it over and who did. Every request is kept. It holds no content: the record only
-- grows, so the copy is the record as it stood at assembled_at.
--
-- patient_id, requested_by and authenticated_by point at tables of other modules and have no foreign key (D-30).
create table record_copy (
    id                    uuid         primary key,
    patient_id            uuid         not null,
    requested_by          uuid         not null,
    requested_by_account  varchar(100) not null,
    requested_at          timestamp    not null,
    delivery              varchar(10)  not null,
    code                  varchar(12)  not null,
    assembled_at          timestamp    not null,
    assembly              integer      not null,
    authenticated_by      uuid,
    authenticated_by_user varchar(100),
    authenticated_at      timestamp,
    handed_over_by        varchar(100),
    handed_over_at        timestamp,
    version               bigint       not null,
    constraint uq_record_copy_code unique (code),
    constraint ck_record_copy_delivery check (delivery in ('PDF', 'PAPER')),
    constraint ck_record_copy_assembly check (assembly >= 1 and assembled_at >= requested_at),
    -- Authenticated whole, or not at all.
    constraint ck_record_copy_authenticated check (
        (authenticated_by is null and authenticated_by_user is null and authenticated_at is null)
        or (authenticated_by is not null and authenticated_by_user is not null and authenticated_at is not null)),
    -- Only a copy on paper, once authenticated, is handed over.
    constraint ck_record_copy_handed_over check (
        (handed_over_by is null and handed_over_at is null)
        or (handed_over_by is not null and handed_over_at is not null and authenticated_at is not null
            and delivery = 'PAPER'))
);

create index ix_record_copy_patient on record_copy (patient_id);

-- Access to a record may now be asked for to authenticate a copy of it, as to report a check (D-95, D-54): a grant
-- is for one or the other.
alter table record_access_grant alter column check_id drop not null;
alter table record_access_grant add column record_copy_id uuid;
alter table record_access_grant add constraint fk_record_access_grant_copy
    foreign key (record_copy_id) references record_copy (id);
alter table record_access_grant add constraint ck_record_access_grant_for
    check ((check_id is null) <> (record_copy_id is null));

-- A copy handed to the patient is one more reading of the record, by the patient's right to it, with no
-- practitioner (D-96); a practitioner reviewing it reads it as usual. Either names the copy.
alter table record_reading alter column practitioner_id drop not null;
alter table record_reading add column record_copy_id uuid;
alter table record_reading add constraint fk_record_reading_copy foreign key (record_copy_id) references record_copy (id);
alter table record_reading drop constraint ck_record_reading_access;
alter table record_reading add constraint ck_record_reading_access
    check (access in ('VISIT_TODAY', 'ACCESS_GRANT', 'SEEN_WITHIN_PERIOD', 'COPY_REQUEST'));
alter table record_reading add constraint ck_record_reading_practitioner
    check ((access = 'COPY_REQUEST' and practitioner_id is null and record_copy_id is not null)
        or (access <> 'COPY_REQUEST' and practitioner_id is not null));
