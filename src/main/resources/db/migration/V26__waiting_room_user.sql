-- The waiting room's screen logs in with a user of its own, who sees that screen and nothing else
-- (D-70).
alter table app_user drop constraint ck_app_user_role;
alter table app_user add constraint ck_app_user_role
    check (role in ('RECEPTION', 'PRACTITIONER', 'ADMIN', 'WAITING_ROOM'));
