-- An appointment whose patient never arrived is absent, no longer cancelled (D-61). The appointments
-- cancelled automatically before this stay cancelled: nothing tells them apart from those
-- cancelled by hand.
--
-- An absent appointment holds its slot no more, like a cancelled one: the overlap constraints of
-- db/vendor/postgresql only cover booked appointments, so they need no change.
--
-- The column auto_cancellation_suspended keeps its name, though it now holds the late-arrival
-- notice: renaming a column is not portable SQL.
alter table appointment drop constraint ck_appointment_status;
alter table appointment add constraint ck_appointment_status check (status in ('BOOKED', 'CANCELLED', 'ABSENT'));
