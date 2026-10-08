-- Registering from the web (D-91).
--
-- An adult's registration waiting to be confirmed from the link in their email: nobody is a patient, nor has an
-- account, until then. Only the SHA-256 of the link's token is kept, and the bcrypt of the password the adult
-- chose. The document's number and the phone are kept as they were typed, separators and all, since the patient
-- module normalizes them once they are registered. A document is not unique here: whoever confirms first is
-- registered, and a registration never confirmed is deleted once it expires.
create table web_registration (
    id              uuid         primary key,
    token_hash      varchar(64)  not null,
    full_name       varchar(150) not null,
    document_type   varchar(20)  not null,
    document_number varchar(40)  not null,
    birth_date      date         not null,
    phone_number    varchar(40),
    email           varchar(254) not null,
    password_hash   varchar(100) not null,
    requested_at    timestamp    not null,
    expires_at      timestamp    not null,
    constraint uq_web_registration_token unique (token_hash)
);

create index ix_web_registration_expires_at on web_registration (expires_at);

-- Where each patient was registered: at reception or on the web. Every patient so far was registered at
-- reception, the only place one could be, so the default says what is true of them.
alter table patient add column registered_via varchar(10) default 'RECEPTION' not null;
alter table patient add constraint ck_patient_registered_via check (registered_via in ('RECEPTION', 'WEB'));

-- An account opened by confirming a registration from the web is recorded with nobody at reception.
alter table patient_account_change drop constraint ck_patient_account_change_action;
alter table patient_account_change drop constraint ck_patient_account_change_by;
alter table patient_account_change add constraint ck_patient_account_change_action check (action in ('OPENED',
    'ACTIVATION_SENT', 'ACTIVATED', 'EMAIL_CHANGED', 'DEACTIVATED', 'REACTIVATED', 'RECOVERY_SENT',
    'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED', 'REGISTERED'));
alter table patient_account_change add constraint ck_patient_account_change_by check (
    (action in ('ACTIVATED', 'RECOVERY_SENT', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED', 'REGISTERED')
        and changed_by is null)
    or (action not in ('ACTIVATED', 'RECOVERY_SENT', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED', 'REGISTERED')
        and changed_by is not null));
