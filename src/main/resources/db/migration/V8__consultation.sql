-- Consultations: what a practitioner recorded of a patient they saw, which makes up the patient's
-- clinical record. Written once and never updated (D-47).
--
-- patient_id and practitioner_id have no foreign key: they point at tables owned by other
-- modules (D-30). There is no reference to an appointment either: a consultation does not come
-- from one, and once the arrival queue exists it will not always have one (D-48).
create table consultation (
    id              uuid           primary key,
    patient_id      uuid           not null,
    practitioner_id uuid           not null,
    recorded_at     timestamp      not null,
    notes           varchar(10000) not null
);

-- The two reads: a patient's record, and a practitioner's last consultation of a patient (D-49).
create index ix_consultation_patient on consultation (patient_id, recorded_at);
create index ix_consultation_patient_practitioner on consultation (patient_id, practitioner_id, recorded_at);

-- The checks, prescriptions and study orders of a consultation, in the order they were written.
-- The foreign key stays inside the module, so it is allowed.
create table consultation_item (
    consultation_id uuid          not null,
    position        integer       not null,
    kind            varchar(20)   not null,
    description     varchar(2000) not null,
    constraint pk_consultation_item primary key (consultation_id, position),
    constraint fk_consultation_item_consultation foreign key (consultation_id) references consultation (id),
    constraint ck_consultation_item_kind check (kind in ('CHECK', 'PRESCRIPTION', 'STUDY_ORDER'))
);
