-- Booking on the web (D-82).
--
-- Reception marks which of a practitioner's reasons a patient may book on the web; none may at first, so
-- nothing is offered until reception says so.
alter table reason add column web_bookable boolean default false not null;

-- Where each appointment was booked: at reception or on the web. Every appointment so far was booked at
-- reception, the only place one could be, so the default says what is true of them.
alter table appointment add column booked_via varchar(10) default 'RECEPTION' not null;
alter table appointment add constraint ck_appointment_booked_via check (booked_via in ('RECEPTION', 'WEB'));
