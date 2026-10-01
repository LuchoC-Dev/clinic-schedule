-- Consent to be reminded on each of the patient's channels, and who gave it: the patient, or the
-- responsible person the channel belongs to (D-77). Null is no consent, which is where every patient
-- already on the books starts: nobody is reminded until reception records it.
alter table patient add column phone_consent varchar(10);
alter table patient add column email_consent varchar(10);

alter table patient add constraint ck_patient_phone_consent check (
    phone_consent is null
    or (phone_consent in ('PATIENT', 'OWNER') and phone_number is not null
        and (phone_consent = 'PATIENT' or phone_owner_relation is not null))
);
alter table patient add constraint ck_patient_email_consent check (
    email_consent is null
    or (email_consent in ('PATIENT', 'OWNER') and email_address is not null
        and (email_consent = 'PATIENT' or email_owner_relation is not null))
);

-- A consent given or withdrawn goes to the history of the details, with who gave it as the value.
alter table patient_details_change drop constraint ck_patient_details_change_field;
alter table patient_details_change add constraint ck_patient_details_change_field check (
    field in ('BIRTH_DATE', 'PHONE', 'EMAIL', 'INSURANCE', 'PHONE_CONSENT', 'EMAIL_CONSENT')
);
