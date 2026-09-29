-- Development data (D-34): the waiting room's user, for the screen on the wall (D-70).
-- Password (BCrypt): sala / sala123.
--
-- A database already in use may have a user of that name: this one is only added when it is free.

insert into app_user (id, username, password_hash, role, practitioner_id, active)
select cast('d4444444-0000-0000-0000-000000000005' as uuid), 'sala',
       '$2a$10$iy539HtD7xFG9njzzJyW0.ic0zncQadTx9u9AGZhDqd8W8RnIKTUi', 'WAITING_ROOM', null, true
where not exists (select 1 from app_user where username = 'sala');
