-- Development data (D-34): a patient with a web account in use (D-79), so the patients' side can be tried
-- right after starting the application. An adult whose identity reception verified, as an account needs.
-- Logs in at /portal/login as DNI 27333444 / paciente123 (BCrypt).
--
-- A database already in use may have that document, or that account: each is only added when it is free.

insert into patient (id, full_name, document_type, document_number, identity_status, birth_date)
select cast('c3333333-0000-0000-0000-000000000003' as uuid), 'Laura Medina', 'DNI', '27333444', 'VERIFIED',
       cast('1979-08-20' as date)
where not exists (select 1 from patient where document_type = 'DNI' and document_number = '27333444');

insert into patient_account (id, patient_id, email, password_hash, status, opened_at)
select cast('e5555555-0000-0000-0000-000000000001' as uuid), cast('c3333333-0000-0000-0000-000000000003' as uuid),
       'laura.medina@mail.com', '$2a$10$zeRJz.QIHcOoalF6JcTme.8QBRNbzEw8TkOG3BJlCl4go1G6n3BJ.', 'ACTIVE',
       cast('2026-10-01 09:00:00' as timestamp)
where exists (select 1 from patient where id = cast('c3333333-0000-0000-0000-000000000003' as uuid))
  and not exists (select 1 from patient_account
                  where patient_id = cast('c3333333-0000-0000-0000-000000000003' as uuid));
