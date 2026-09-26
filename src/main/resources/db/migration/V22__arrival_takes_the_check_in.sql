-- The arrival leaves the appointment (D-65): the queues keep who came, and when. The check-ins
-- recorded on appointments before the queues existed move here, to the arrival module, which answers
-- when each appointment's patient arrived; no queue session is made up for them.
--
-- appointment_id points at another module's table and has no foreign key (D-30).
create table appointment_check_in (
    appointment_id uuid      primary key,
    checked_in_at  timestamp not null
);

insert into appointment_check_in (appointment_id, checked_in_at)
select id, checked_in_at from appointment where checked_in_at is not null;

alter table appointment drop column checked_in_at;
