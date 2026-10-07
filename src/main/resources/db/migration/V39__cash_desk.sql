-- The cash desk (D-87). A visit is each time reception puts a patient into a queue: its id is the queue's
-- entry. A charge is what was paid for it, recorded by whom and when. Both are only ever inserted, never
-- changed nor deleted: a visit not charged yet - an emergency's - is a visit without a charge.
--
-- patient_id points at another module's table and has no foreign key (D-30); visit_id points at this
-- module's own table, so it has one.
create table cash_visit (
    id          uuid      primary key,
    patient_id  uuid      not null,
    arrived_at  timestamp not null,
    emergency   boolean   not null
);

create table cash_charge (
    id          uuid           primary key,
    visit_id    uuid           not null,
    amount      numeric(12, 2) not null,
    means       varchar(10)    not null,
    recorded_by varchar(50)    not null,
    recorded_at timestamp      not null,
    constraint fk_cash_charge_visit foreign key (visit_id) references cash_visit (id),
    -- A visit is charged once: two requests at once cannot both charge it.
    constraint uq_cash_charge_visit unique (visit_id),
    constraint ck_cash_charge_amount check (amount > 0),
    -- Named, so the part that adds the other means drops and adds it again.
    constraint ck_cash_charge_means check (means in ('CASH'))
);

-- The two reads of a day: the charges recorded that day, and the visits that came that day.
create index ix_cash_charge_recorded_at on cash_charge (recorded_at);
create index ix_cash_visit_arrived_at on cash_visit (arrived_at);
