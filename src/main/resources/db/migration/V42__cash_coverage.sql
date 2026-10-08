-- Visits covered by a health insurance (D-90). The insurer is asked outside the application; what is kept is
-- the outcome: the patient's insurance as it was that day, and the token the insurer asked for that visit, if
-- any. A coverage marked by mistake is withdrawn as a fact of its own. Both are only ever inserted, never changed
-- nor deleted, as the charges and the refunds.
--
-- Each is keyed by its visit: two requests at once cannot both cover a visit, nor withdraw its coverage twice.
create table cash_coverage (
    visit_id      uuid        primary key,
    insurer       varchar(100) not null,
    member_number varchar(30),
    plan          varchar(50),
    token         varchar(50),
    recorded_by   varchar(50) not null,
    recorded_at   timestamp   not null,
    constraint fk_cash_coverage_visit foreign key (visit_id) references cash_visit (id)
);

create table cash_coverage_withdrawal (
    visit_id    uuid         primary key,
    reason      varchar(200) not null,
    recorded_by varchar(50)  not null,
    recorded_at timestamp    not null,
    constraint fk_cash_coverage_withdrawal_coverage foreign key (visit_id) references cash_coverage (visit_id)
);

-- A day's covered visits.
create index ix_cash_coverage_recorded_at on cash_coverage (recorded_at);

-- A copay is a charge as any other; it says so, so the day's cash desk can tell it apart. The charges kept so far
-- were all private.
alter table cash_charge add column copay boolean default false not null;
alter table cash_charge alter column copay drop default;
