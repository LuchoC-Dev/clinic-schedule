-- Family groups (D-84): formed by reception, so a family books from one account. A patient may be in several.
create table family_group (
    id         uuid         primary key,
    name       varchar(100) not null,
    created_at timestamp    not null
);

-- Who is in each group, and whether they share their appointments with it. A child is added at the request of
-- a member who is their parent or guardian, named with the relation. patient_id and requested_by point at the
-- patient module's table and have no foreign key (D-30).
create table family_member (
    group_id     uuid        not null,
    patient_id   uuid        not null,
    shares       boolean     not null,
    requested_by uuid,
    relation     varchar(10),
    added_at     timestamp   not null,
    constraint pk_family_member primary key (group_id, patient_id),
    constraint fk_family_member_group foreign key (group_id) references family_group (id),
    constraint ck_family_member_relation check (relation in ('MOTHER', 'FATHER', 'GUARDIAN')),
    constraint ck_family_member_requested check ((requested_by is null and relation is null)
        or (requested_by is not null and relation is not null))
);

create index ix_family_member_patient on family_member (patient_id);

-- What happened to each group, to whom, when and by whom. Only ever inserted.
create table family_group_change (
    id         uuid        primary key,
    group_id   uuid        not null,
    action     varchar(20) not null,
    patient_id uuid,
    changed_at timestamp   not null,
    changed_by varchar(50) not null,
    constraint fk_family_group_change_group foreign key (group_id) references family_group (id),
    constraint ck_family_group_change_action check (action in ('CREATED', 'JOINED', 'CHILD_ADDED', 'REMOVED',
        'SHARING_STARTED', 'SHARING_STOPPED')),
    constraint ck_family_group_change_patient check ((action = 'CREATED' and patient_id is null)
        or (action <> 'CREATED' and patient_id is not null))
);

create index ix_family_group_change_group on family_group_change (group_id);
