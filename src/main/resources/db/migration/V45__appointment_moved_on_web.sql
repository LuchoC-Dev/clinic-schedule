-- Moving an appointment on the web (D-93).
--
-- Whether the patient ever moved the appointment on the web, which reception reads on the agenda. When, and
-- from what time, is not kept: no history of an appointment's times is (phase 5). Nobody could move one on the
-- web before, so the default says what is true of the appointments already there.
alter table appointment add column moved_on_web boolean default false not null;
