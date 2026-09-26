-- A patient may come into a queue with their appointment of the day (D-64): the entry keeps which
-- appointment, its time — which places them in the order — and the practitioner they are kept for.
-- appointment_id and reserved_for point at tables of other modules and have no foreign key (D-30).
alter table session_entry add column appointment_id uuid;
alter table session_entry add column appointment_at timestamp;
alter table session_entry add column reserved_for uuid;

-- The appointment module asks which of its appointments have their patient in a queue.
create index ix_session_entry_appointment on session_entry (appointment_id);
