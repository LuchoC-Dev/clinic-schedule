-- What is recorded of a cancellation (D-76): when, by whom, how — by hand or by a time off —, who
-- asked for it — the patient or the practice — and reception's optional note. Columns of the
-- appointment and not a table of their own: an appointment is cancelled once, and never again.
--
-- The appointments cancelled before this have none of it, since nothing told who cancelled them nor
-- whether by hand or automatically; nothing is made up for them. So the four that go together are all
-- there or all missing, and only on a cancelled appointment.
alter table appointment add column cancelled_at timestamp;
alter table appointment add column cancelled_by varchar(50);
alter table appointment add column cancellation_origin varchar(20);
alter table appointment add column cancellation_requested_by varchar(20);
alter table appointment add column cancellation_note varchar(200);

alter table appointment add constraint ck_appointment_cancellation check (
    (cancelled_at is null and cancelled_by is null and cancellation_origin is null
        and cancellation_requested_by is null and cancellation_note is null)
    or (status = 'CANCELLED' and cancelled_at is not null and cancelled_by is not null
        and cancellation_origin is not null and cancellation_requested_by is not null));
alter table appointment add constraint ck_appointment_cancellation_origin
    check (cancellation_origin in ('BY_HAND', 'TIME_OFF'));
alter table appointment add constraint ck_appointment_cancellation_requested_by
    check (cancellation_requested_by in ('PATIENT', 'PRACTICE'));
-- A time off cancels on the practice's behalf.
alter table appointment add constraint ck_appointment_cancellation_time_off
    check (cancellation_origin <> 'TIME_OFF' or cancellation_requested_by = 'PRACTICE');

-- The list of cancellations reads them by when they were made.
create index ix_appointment_cancelled_at on appointment (cancelled_at);
