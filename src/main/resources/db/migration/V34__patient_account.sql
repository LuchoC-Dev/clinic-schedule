-- The patients' web accounts (D-79): one per patient at most, opened by reception and activated by the
-- patient, who chooses the password then. The email is the account's own, apart from the patient's contact
-- email, and not unique: a family may share one.
--
-- patient_id points at the patient module's table and has no foreign key (D-30).
create table patient_account (
    id            uuid         primary key,
    patient_id    uuid         not null,
    email         varchar(254) not null,
    password_hash varchar(100),
    status        varchar(10)  not null,
    opened_at     timestamp    not null,
    constraint uq_patient_account_patient unique (patient_id),
    constraint ck_patient_account_status check (status in ('PENDING', 'ACTIVE', 'INACTIVE')),
    -- A pending account waits for its password, and an active one has it; an inactive one may have either.
    constraint ck_patient_account_password check (
        (status = 'PENDING' and password_hash is null)
        or (status = 'ACTIVE' and password_hash is not null)
        or status = 'INACTIVE')
);

-- The link that activates an account: one at most per account, kept only as the SHA-256 of its token, until
-- it is used, expires or the account is deactivated.
create table patient_account_activation (
    account_id uuid        primary key,
    token_hash varchar(64) not null,
    expires_at timestamp   not null,
    constraint uq_patient_account_activation_token unique (token_hash),
    constraint fk_patient_account_activation_account foreign key (account_id) references patient_account (id)
);

-- What was done to each account, when and by whom. Only ever inserted. changed_by is the username of whoever
-- at reception did it, and empty for what the patient did: activating it.
create table patient_account_change (
    id         uuid        primary key,
    account_id uuid        not null,
    action     varchar(20) not null,
    changed_at timestamp   not null,
    changed_by varchar(50),
    constraint fk_patient_account_change_account foreign key (account_id) references patient_account (id),
    constraint ck_patient_account_change_action check (action in ('OPENED', 'ACTIVATION_SENT', 'ACTIVATED',
        'EMAIL_CHANGED', 'DEACTIVATED', 'REACTIVATED')),
    constraint ck_patient_account_change_by check (
        (action = 'ACTIVATED' and changed_by is null)
        or (action <> 'ACTIVATED' and changed_by is not null))
);

create index ix_patient_account_change_account on patient_account_change (account_id);
