-- The health insurance a patient is covered by: the insurer, their member number and their plan, free
-- text, all of it optional (D-74). None of the patients already on the books has one, which says
-- nothing about whether they pay privately: nobody asked. A member number or a plan goes with the
-- insurer's name.
alter table patient add column insurance_insurer varchar(100);
alter table patient add column insurance_member_number varchar(30);
alter table patient add column insurance_plan varchar(50);

alter table patient add constraint ck_patient_insurance check (
    insurance_insurer is not null or (insurance_member_number is null and insurance_plan is null)
);

-- A change to the insurance goes to the history of the details like any other: the insurer as the
-- value, and the member number and the plan each in a column of its own, before and after.
alter table patient_details_change add column member_number_before varchar(30);
alter table patient_details_change add column plan_before varchar(50);
alter table patient_details_change add column member_number_after varchar(30);
alter table patient_details_change add column plan_after varchar(50);

alter table patient_details_change drop constraint ck_patient_details_change_field;
alter table patient_details_change add constraint ck_patient_details_change_field check (
    field in ('BIRTH_DATE', 'PHONE', 'EMAIL', 'INSURANCE')
);
