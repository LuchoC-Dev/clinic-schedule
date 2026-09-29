-- The admins read who read which clinical record (D-71). Each reading now keeps the username the
-- practitioner logged in as; those recorded before have none, and say so.
alter table record_reading add column read_by varchar(50);

-- What the admins search by: the days, and the patient within them.
create index ix_record_reading_read_at on record_reading (read_at);
create index ix_record_reading_patient on record_reading (patient_id, read_at);
