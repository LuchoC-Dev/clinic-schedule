-- Children under 13 registered on the web by an adult (D-92).
--
-- The children of a registration waiting to be confirmed, as they were typed, the relation included: forgotten
-- with their registration, confirmed or not. The document's number is kept with its separators, as the adult's.
create table web_registration_child (
    registration_id uuid         not null,
    position        integer      not null,
    full_name       varchar(150) not null,
    document_type   varchar(20)  not null,
    document_number varchar(40)  not null,
    birth_date      date         not null,
    relation        varchar(10)  not null,
    constraint pk_web_registration_child primary key (registration_id, position),
    constraint fk_web_registration_child_registration foreign key (registration_id)
        references web_registration (id) on delete cascade,
    constraint ck_web_registration_child_relation check (relation in ('MOTHER', 'FATHER', 'GUARDIAN'))
);

-- Whether reception saw what proves a child's relation with whoever asked for them. Every child so far was added
-- by reception with it seen, so the default says what is true of them; one registered on the web waits for it.
alter table family_member add column relation_proved boolean default true not null;
alter table family_member add constraint ck_family_member_proved check (relation_proved
    or requested_by is not null);

alter table family_group_change drop constraint ck_family_group_change_action;
alter table family_group_change add constraint ck_family_group_change_action check (action in ('CREATED', 'JOINED',
    'CHILD_ADDED', 'REMOVED', 'SHARING_STARTED', 'SHARING_STOPPED', 'RELATION_PROVED'));

-- A child asked to be registered from an account, whatever came of it, recorded with nobody at reception: it is
-- also what limits how many a day.
alter table patient_account_change drop constraint ck_patient_account_change_action;
alter table patient_account_change drop constraint ck_patient_account_change_by;
alter table patient_account_change add constraint ck_patient_account_change_action check (action in ('OPENED',
    'ACTIVATION_SENT', 'ACTIVATED', 'EMAIL_CHANGED', 'DEACTIVATED', 'REACTIVATED', 'RECOVERY_SENT',
    'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED', 'REGISTERED', 'CHILD_REQUESTED'));
alter table patient_account_change add constraint ck_patient_account_change_by check (
    (action in ('ACTIVATED', 'RECOVERY_SENT', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED', 'REGISTERED',
        'CHILD_REQUESTED') and changed_by is null)
    or (action not in ('ACTIVATED', 'RECOVERY_SENT', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED', 'REGISTERED',
        'CHILD_REQUESTED') and changed_by is not null));
