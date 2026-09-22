-- Patients. The minimum needed to book an appointment: an internal id, a full name and,
-- optionally, the document they identify themselves with.
--
-- The document is a type plus a number, not a bare number: DNI 30111222 and passport
-- 30111222 are different people, so the unique constraint covers the pair. Both columns are
-- null together for a patient without a document, and PostgreSQL and H2 both allow repeated
-- nulls in a unique constraint, which gives exactly the rule asked for — unique when given,
-- any number of patients without one — with no partial index and no engine-specific SQL
-- (D-03, D-09).
--
-- Two patients may share a name, so full_name is not unique.
create table patient (
    id              uuid         primary key,
    full_name       varchar(150) not null,
    document_type   varchar(20),
    document_number varchar(20),
    constraint uq_patient_document unique (document_type, document_number),
    constraint ck_patient_document_type check (
        document_type is null or document_type in ('DNI', 'LC', 'LE', 'CI', 'PASSPORT')
    ),
    constraint ck_patient_document_complete check (
        (document_type is null and document_number is null)
        or (document_type is not null and document_number is not null)
    )
);

-- Reception searches by name far more often than by document.
create index ix_patient_full_name on patient (full_name);
