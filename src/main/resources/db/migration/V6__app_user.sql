-- Users of the application. Reception manages the practice; a practitioner reads only their own
-- agenda (D-04, D-34).
--
-- The table is app_user because "user" is reserved in PostgreSQL (D-03). A user with the
-- PRACTITIONER role points at the practitioner it stands for. There is no foreign key to the
-- practitioner table: it belongs to another module, and D-30 keeps foreign keys inside a
-- module. The reference is set by whoever creates the user, which in v1 is only the seed.
create table app_user (
    id              uuid         primary key,
    username        varchar(50)  not null,
    password_hash   varchar(100) not null,
    role            varchar(20)  not null,
    practitioner_id uuid,
    active          boolean      default true not null,
    constraint uq_app_user_username unique (username),
    constraint ck_app_user_role check (role in ('RECEPTION', 'PRACTITIONER')),
    constraint ck_app_user_practitioner check (
        (role = 'PRACTITIONER' and practitioner_id is not null)
        or (role = 'RECEPTION' and practitioner_id is null)
    )
);
