-- Refunds (D-88): money given back from a charge, a movement of its own with its amount, why, who and when.
-- Only ever inserted, never changed nor deleted, as the charges.
--
-- A visit given back whole may be charged again, so a visit has its charges numbered by their place, and a
-- charge its refunds. Two requests at once charging the visit, or giving back from the charge, take the same
-- number, and the unique keys refuse the second: a visit never has two charges with something left, and no
-- more is given back than was charged. The charges kept so far are each their visit's first.
alter table cash_charge add column charge_no integer default 1 not null;
alter table cash_charge alter column charge_no drop default;
alter table cash_charge drop constraint uq_cash_charge_visit;
alter table cash_charge add constraint uq_cash_charge_visit_no unique (visit_id, charge_no);

create table cash_refund (
    id          uuid           primary key,
    charge_id   uuid           not null,
    refund_no   integer        not null,
    amount      numeric(12, 2) not null,
    reason      varchar(200)   not null,
    recorded_by varchar(50)    not null,
    recorded_at timestamp      not null,
    constraint fk_cash_refund_charge foreign key (charge_id) references cash_charge (id),
    constraint uq_cash_refund_charge_no unique (charge_id, refund_no),
    constraint ck_cash_refund_amount check (amount > 0)
);

-- A day's refunds, whatever day their charge was.
create index ix_cash_refund_recorded_at on cash_refund (recorded_at);
