-- No two booked appointments overlap, for the same practitioner or for the same patient,
-- guaranteed by the database itself (D-09, D-21, D-44).
--
-- The application checks overlap before booking, but two receptionists booking the same slot
-- at the same time can both find it free: each reads the agenda before the other writes. These
-- constraints refuse the second insert, and the persistence adapter answers it as the same
-- refusal the application would have given.
--
-- PostgreSQL only, which is why this lives in db/vendor/postgresql: H2 has no exclusion
-- constraints. The range is [start, start + duration), so back-to-back appointments do not
-- collide, and cancelled appointments are left out, as in the application's rule.
--
-- btree_gist lets a GiST index compare uuids with "=" next to the range's "&&"; it ships with
-- PostgreSQL, and creating it takes a role allowed to create extensions.
create extension if not exists btree_gist;

alter table appointment add constraint ex_appointment_practitioner_overlap
    exclude using gist (
        practitioner_id with =,
        tsrange(starts_at, starts_at + duration_minutes * interval '1 minute') with &&
    ) where (status = 'BOOKED');

alter table appointment add constraint ex_appointment_patient_overlap
    exclude using gist (
        patient_id with =,
        tsrange(starts_at, starts_at + duration_minutes * interval '1 minute') with &&
    ) where (status = 'BOOKED');
