-- Development data (D-34): an admin to log in with, who administers the users (D-68).
-- Password (BCrypt): admin / admin123.
--
-- A database already in use may have an admin of its own: this one is only added when the username
-- is free.

insert into app_user (id, username, password_hash, role, practitioner_id, active)
select cast('d4444444-0000-0000-0000-000000000004' as uuid), 'admin',
       '$2a$10$HDjBxTmat1h5azmcWEVOA.Vg3GWIwDZVrf4xFwZVvoph/IvSUtHhS', 'ADMIN', null, true
where not exists (select 1 from app_user where username = 'admin');
