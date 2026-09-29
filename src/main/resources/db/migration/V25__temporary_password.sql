-- A password set by an admin is temporary: its user must change it the next time they log in, before
-- doing anything else (D-69). Users already there, and those of the development data, keep theirs.
alter table app_user add column must_change_password boolean default false not null;

-- A user changing their own password is recorded too, by themselves.
alter table app_user_change drop constraint ck_app_user_change_action;
alter table app_user_change add constraint ck_app_user_change_action
    check (action in ('CREATED', 'DEACTIVATED', 'REACTIVATED', 'PASSWORD_RESET', 'PASSWORD_CHANGED'));
