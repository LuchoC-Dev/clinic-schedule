-- How far the practice knows who a patient is, the alias of one who gave no name, and the history
-- of every change to their identity (D-60).
--
-- Every patient registered so far gave a name and nobody verified a document, so they are all
-- DECLARED. The default stays for rows written outside the application, such as the development
-- data; the application always says which.
alter table patient add column identity_status varchar(10) default 'DECLARED' not null;
alter table patient add column alias varchar(7);

alter table patient add constraint uq_patient_alias unique (alias);
alter table patient add constraint ck_patient_identity_status check (
    identity_status in ('ALIAS', 'DECLARED', 'VERIFIED')
);
-- Going by an alias needs one, and a verified identity needs the document that was seen.
alter table patient add constraint ck_patient_alias check (identity_status <> 'ALIAS' or alias is not null);
alter table patient add constraint ck_patient_verified check (identity_status <> 'VERIFIED' or document_type is not null);

-- Only ever inserted. changed_by is the username of whoever changed it.
create table patient_identity_change (
    id                     uuid         primary key,
    patient_id             uuid         not null,
    changed_at             timestamp    not null,
    changed_by             varchar(50)  not null,
    full_name_before       varchar(150) not null,
    document_type_before   varchar(20),
    document_number_before varchar(20),
    identity_status_before varchar(10)  not null,
    full_name_after        varchar(150) not null,
    document_type_after    varchar(20),
    document_number_after  varchar(20),
    identity_status_after  varchar(10)  not null,
    constraint fk_patient_identity_change_patient foreign key (patient_id) references patient (id)
);

-- The one read: a patient's history, on their screen.
create index ix_patient_identity_change_patient on patient_identity_change (patient_id);
