-- Files attached to a consultation or a check (D-57). What describes each file and the file itself
-- live in two tables, so listing a record's attachments never reads the files; the content table
-- is what an object store replaces when the files outgrow the database. Nothing is ever updated
-- or deleted.
--
-- patient_id and uploaded_by point at tables of other modules and have no foreign key (D-30); the
-- consultation and the check are this module's own.
create table attachment (
    id              uuid         primary key,
    patient_id      uuid         not null,
    consultation_id uuid,
    check_id        uuid,
    uploaded_by     uuid         not null,
    uploaded_at     timestamp    not null,
    file_name       varchar(255) not null,
    media_type      varchar(50)  not null,
    size_bytes      bigint       not null,
    sha256          varchar(64)  not null,
    constraint fk_attachment_consultation foreign key (consultation_id) references consultation (id),
    constraint fk_attachment_check foreign key (check_id) references clinical_check (id),
    -- It belongs to one thing: a consultation or a check.
    constraint ck_attachment_owner check ((consultation_id is null) <> (check_id is null)),
    constraint ck_attachment_size check (size_bytes > 0)
);

create index ix_attachment_patient on attachment (patient_id);
create index ix_attachment_consultation on attachment (consultation_id);
create index ix_attachment_check on attachment (check_id);

create table attachment_content (
    attachment_id uuid  primary key,
    content       bytea not null,
    constraint fk_attachment_content_attachment foreign key (attachment_id) references attachment (id)
);

-- A result may now be only the check's attachments, with no text (D-57): the text leaves the rule
-- that the result's columns are all set or all empty.
alter table clinical_check drop constraint ck_clinical_check_result;
alter table clinical_check add constraint ck_clinical_check_result check (
    (result_author is null and result_written_at is null and result_text is null)
    or (result_author is not null and result_written_at is not null));
