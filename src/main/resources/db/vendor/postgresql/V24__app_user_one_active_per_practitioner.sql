-- A practitioner has one active user at most (D-68). The application checks it before saving, so
-- the answer is a clear refusal; this is what holds when two admins give the same practitioner a
-- user at once. A partial index is PostgreSQL's alone, hence here and not in db/migration (D-44).
create unique index uq_app_user_active_practitioner on app_user (practitioner_id) where active;
