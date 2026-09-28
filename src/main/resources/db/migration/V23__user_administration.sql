-- User administration (D-68). An admin creates the practice's users, deactivates and reactivates
-- them, and resets their passwords; each of those actions is recorded.
--
-- The admin is a role of its own, which stands for no practitioner: only a practitioner's user
-- does.
alter table app_user drop constraint ck_app_user_role;
alter table app_user drop constraint ck_app_user_practitioner;

alter table app_user add constraint ck_app_user_role check (role in ('RECEPTION', 'PRACTITIONER', 'ADMIN'));
alter table app_user add constraint ck_app_user_practitioner check (
    (role = 'PRACTITIONER' and practitioner_id is not null)
    or (role <> 'PRACTITIONER' and practitioner_id is null)
);

-- What an admin did to a user, by whom and when. Only ever inserted. changed_by is a username,
-- as everywhere else something is recorded: usernames never change and are never used again.
create table app_user_change (
    id         uuid        primary key,
    user_id    uuid        not null,
    action     varchar(20) not null,
    changed_by varchar(50) not null,
    changed_at timestamp   not null,
    constraint fk_app_user_change_user foreign key (user_id) references app_user (id),
    constraint ck_app_user_change_action check (action in ('CREATED', 'DEACTIVATED', 'REACTIVATED', 'PASSWORD_RESET'))
);

create index ix_app_user_change_user on app_user_change (user_id);
