-- Orders for a check: a practitioner who needs a check they cannot do authorizes it, during a
-- consultation for now; one check fulfils it, or its author cancels it with a reason (D-55). The
-- table is check_order because order is a reserved word in SQL.
--
-- patient_id and ordered_by point at tables of other modules and have no foreign key (D-30); the
-- consultation and the check that fulfilled it are this module's own, so they have one. A check
-- fulfils one order at most, hence the unique constraint.
create table check_order (
    id                  uuid          primary key,
    version             bigint        default 0 not null,
    patient_id          uuid          not null,
    ordered_by          uuid          not null,
    ordered_at          timestamp     not null,
    consultation_id     uuid          not null,
    description         varchar(2000) not null,
    status              varchar(20)   not null,
    fulfilled_by        uuid,
    cancelled_at        timestamp,
    cancellation_reason varchar(500),
    constraint fk_check_order_consultation foreign key (consultation_id) references consultation (id),
    constraint fk_check_order_check foreign key (fulfilled_by) references clinical_check (id),
    constraint uq_check_order_fulfilled_by unique (fulfilled_by),
    constraint ck_check_order_status check (status in ('OPEN', 'FULFILLED', 'CANCELLED')),
    constraint ck_check_order_fulfilled check ((status = 'FULFILLED') = (fulfilled_by is not null)),
    constraint ck_check_order_cancelled check (
        (status = 'CANCELLED') = (cancelled_at is not null and cancellation_reason is not null))
);

create index ix_check_order_patient on check_order (patient_id);

-- An order is not an item of a consultation any more; only prescriptions are. The database starts
-- empty for this version, so no item of that kind is carried over (D-55).
alter table consultation_item drop constraint ck_consultation_item_kind;
alter table consultation_item add constraint ck_consultation_item_kind check (kind in ('PRESCRIPTION'));
