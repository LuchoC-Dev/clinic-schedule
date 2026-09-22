-- Patients. The minimum needed to book an appointment: an internal id, a full name and an
-- optional national ID. There is no patient file yet, and patients have no account.
--
-- The internal id is not the national ID. A unique constraint on national_id gives exactly
-- the rule asked for: unique when given, and any number of patients without one, because
-- both PostgreSQL and H2 allow repeated nulls in a unique constraint. No partial index is
-- needed, which keeps the SQL portable (D-03, D-09).
--
-- Two patients may share a name, so full_name is not unique.
create table patient (
    id          uuid         primary key,
    full_name   varchar(150) not null,
    national_id varchar(20),
    constraint uq_patient_national_id unique (national_id)
);

-- Reception searches by name far more often than by national ID.
create index ix_patient_full_name on patient (full_name);
