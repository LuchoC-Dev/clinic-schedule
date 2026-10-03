-- Cancelling on the web (D-83).
--
-- Two more ways an appointment is cancelled: by the patient from their web account, and from the link in an
-- email. Both are the patient's asking.
alter table appointment drop constraint ck_appointment_cancellation_origin;
alter table appointment add constraint ck_appointment_cancellation_origin
    check (cancellation_origin in ('BY_HAND', 'TIME_OFF', 'ONLINE', 'EMAIL_LINK'));
alter table appointment add constraint ck_appointment_cancellation_patient
    check (cancellation_origin not in ('ONLINE', 'EMAIL_LINK') or cancellation_requested_by = 'PATIENT');

-- The links to cancel an appointment, sent in its reminders and in the confirmation of a web booking: only the
-- SHA-256 of each token, the appointment and the time it was about, so a link stops working once the
-- appointment is moved. appointment_id points at the appointment module's table and has no foreign key (D-30).
create table cancel_link (
    token_hash     varchar(64) primary key,
    appointment_id uuid        not null,
    starts_at      timestamp   not null
);
