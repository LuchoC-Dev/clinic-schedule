-- What the practice knows about a patient besides who they are: when they were born and how to reach
-- them, and the history of every change to it (D-73).
--
-- All of it is optional, and the patients already on the books have none of it. The phone and the
-- email each say whose they are: the patient's own when the owner columns are null, or a responsible
-- person's — a mother, a daughter — named beside it. The email is not unique: two brothers may carry
-- their mother's.
alter table patient add column birth_date date;
alter table patient add column phone_number varchar(16);
alter table patient add column phone_owner_name varchar(150);
alter table patient add column phone_owner_relation varchar(20);
alter table patient add column email_address varchar(254);
alter table patient add column email_owner_name varchar(150);
alter table patient add column email_owner_relation varchar(20);

alter table patient add constraint ck_patient_phone_owner_relation check (
    phone_owner_relation is null
    or phone_owner_relation in ('MOTHER', 'FATHER', 'GUARDIAN', 'PARTNER', 'CHILD', 'OTHER')
);
alter table patient add constraint ck_patient_email_owner_relation check (
    email_owner_relation is null
    or email_owner_relation in ('MOTHER', 'FATHER', 'GUARDIAN', 'PARTNER', 'CHILD', 'OTHER')
);
-- An owner is a name and a relation together, and belongs to a phone or an email that is there.
alter table patient add constraint ck_patient_phone_owner check (
    (phone_owner_name is null and phone_owner_relation is null)
    or (phone_owner_name is not null and phone_owner_relation is not null and phone_number is not null)
);
alter table patient add constraint ck_patient_email_owner check (
    (email_owner_name is null and email_owner_relation is null)
    or (email_owner_name is not null and email_owner_relation is not null and email_address is not null)
);

-- Only ever inserted: one row for each detail that changed. The value is the detail as it is kept —
-- a date as 1981-03-12, a phone's digits, an email — and null when there was none; the person it
-- belonged to is kept apart, so a screen can say it in its own language. changed_by is the username
-- of whoever changed it.
create table patient_details_change (
    id                    uuid         primary key,
    patient_id            uuid         not null,
    changed_at            timestamp    not null,
    changed_by            varchar(50)  not null,
    field                 varchar(20)  not null,
    value_before          varchar(254),
    owner_name_before     varchar(150),
    owner_relation_before varchar(20),
    value_after           varchar(254),
    owner_name_after      varchar(150),
    owner_relation_after  varchar(20),
    constraint fk_patient_details_change_patient foreign key (patient_id) references patient (id),
    constraint ck_patient_details_change_field check (field in ('BIRTH_DATE', 'PHONE', 'EMAIL'))
);

-- The one read: a patient's history, on their screen.
create index ix_patient_details_change_patient on patient_details_change (patient_id);
