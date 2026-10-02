-- What happened to each reminder of an appointment (D-78): sent, failed — tried once —, or not sent and
-- why, on which channel and when. Only ever inserted. starts_at is the appointment's time it was about,
-- so an appointment moved afterwards is reminded again; the patient and the practitioner are kept to list
-- it without asking the appointment module.
--
-- The address it went to is not kept, only a bcrypt hash of it, and the text is not kept at all.
--
-- appointment_id, patient_id and practitioner_id point at other modules' tables and have no foreign
-- key (D-30).
create table reminder (
    id              uuid        primary key,
    appointment_id  uuid        not null,
    patient_id      uuid        not null,
    practitioner_id uuid        not null,
    starts_at       timestamp   not null,
    channel         varchar(10) not null,
    outcome         varchar(10) not null,
    reason          varchar(20),
    address_hash    varchar(60),
    recorded_at     timestamp   not null,
    constraint ck_reminder_channel check (channel in ('EMAIL')),
    constraint ck_reminder_outcome check (outcome in ('SENT', 'FAILED', 'NOT_SENT')),
    constraint ck_reminder_reason check (reason in ('NO_EMAIL', 'NO_CONSENT', 'CONFIDENTIAL')),
    -- A reason exactly when it was not sent; an address exactly when it went, or was tried.
    constraint ck_reminder_not_sent check (
        (outcome = 'NOT_SENT' and reason is not null and address_hash is null)
        or (outcome <> 'NOT_SENT' and reason is null and address_hash is not null))
);

-- The two reads: an appointment's reminders, before reminding it, and the reminders of some days.
create index ix_reminder_appointment on reminder (appointment_id);
create index ix_reminder_starts_at on reminder (starts_at);
