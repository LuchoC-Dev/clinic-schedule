-- A file attached to the patient's record as a whole — a record kept on paper before the application,
-- a report never uploaded — belongs to neither a consultation nor a check (D-94): both columns empty.
-- It still belongs to one thing at most.
alter table attachment drop constraint ck_attachment_owner;
alter table attachment add constraint ck_attachment_owner check (consultation_id is null or check_id is null);
