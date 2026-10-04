-- The patients' passwords (D-86): failed logins in a row lock an account for a while, a link by email lets a
-- patient choose a new one, and the account's history records both and the patient's own changes.
alter table patient_account add column failed_logins integer default 0 not null;
alter table patient_account add column locked_until timestamp;
alter table patient_account add constraint ck_patient_account_failed_logins check (failed_logins >= 0);

-- The link to choose a new password: one at most per account, kept only as the SHA-256 of its token, until it
-- is used or expires; when it was issued, so another is not sent too soon.
create table patient_account_password_reset (
    account_id uuid        primary key,
    token_hash varchar(64) not null,
    issued_at  timestamp   not null,
    expires_at timestamp   not null,
    constraint uq_patient_account_password_reset_token unique (token_hash),
    constraint fk_patient_account_password_reset_account foreign key (account_id) references patient_account (id)
);

-- What the patient or the web does is recorded with nobody at reception; reception does the rest.
alter table patient_account_change drop constraint ck_patient_account_change_action;
alter table patient_account_change drop constraint ck_patient_account_change_by;
alter table patient_account_change add constraint ck_patient_account_change_action check (action in ('OPENED',
    'ACTIVATION_SENT', 'ACTIVATED', 'EMAIL_CHANGED', 'DEACTIVATED', 'REACTIVATED', 'RECOVERY_SENT',
    'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED'));
alter table patient_account_change add constraint ck_patient_account_change_by check (
    (action in ('ACTIVATED', 'RECOVERY_SENT', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED')
        and changed_by is null)
    or (action not in ('ACTIVATED', 'RECOVERY_SENT', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'LOCKED')
        and changed_by is not null));
