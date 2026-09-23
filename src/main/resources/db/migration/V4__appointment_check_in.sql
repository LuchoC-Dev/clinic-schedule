-- What happens to an appointment on the day: the patient's arrival, and whether reception was
-- warned they would be late (D-06, D-07). Two columns on the appointment itself, not a table
-- of their own: v1 needs no more states, and attendance will replace them.
--
-- Added with ALTER rather than by rewriting V1: the schema grows by stages, and this is what
-- that looks like once a table already exists.
alter table appointment add column checked_in_at timestamp;
alter table appointment add column auto_cancellation_suspended boolean default false not null;

-- The sweep that cancels overdue appointments reads booked ones by start time.
create index ix_appointment_status_start on appointment (status, starts_at);
