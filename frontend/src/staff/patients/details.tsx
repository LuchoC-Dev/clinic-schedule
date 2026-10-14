import { Group, Stack, TextInput } from '@mantine/core';

import { type Contact, ContactFields, contactOf, NO_CONTACT } from '../components/fields';
import type { Patient } from '../types';

/**
 * A patient's details, every part optional (D-73, D-74): the date of birth, the phone and the email each saying
 * whose they are, and the health insurance as free text. Left out, a detail is cleared.
 */
export interface Details {
  birthDate: string;
  phone: Contact;
  email: Contact;
  insurer: string;
  memberNumber: string;
  plan: string;
}

export const NO_DETAILS: Details = {
  birthDate: '', phone: NO_CONTACT, email: NO_CONTACT, insurer: '', memberNumber: '', plan: '',
};

export function detailsOf(patient: Patient): Details {
  return {
    birthDate: patient.birthDate ?? '',
    phone: patient.phone
      ? { value: patient.phone.number, relation: patient.phone.owner?.relation ?? '', ownerName: patient.phone.owner?.name ?? '' }
      : NO_CONTACT,
    email: patient.email
      ? { value: patient.email.address, relation: patient.email.owner?.relation ?? '', ownerName: patient.email.owner?.name ?? '' }
      : NO_CONTACT,
    insurer: patient.insurance?.insurer ?? '',
    memberNumber: patient.insurance?.memberNumber ?? '',
    plan: patient.insurance?.plan ?? '',
  };
}

/** The details as the API takes them. */
export function detailsBody(details: Details) {
  const blank = (text: string) => text.trim() || null;
  return {
    birthDate: details.birthDate || null,
    phone: contactOf(details.phone, 'number'),
    email: contactOf(details.email, 'address'),
    insurance: details.insurer.trim()
      ? { insurer: details.insurer.trim(), memberNumber: blank(details.memberNumber), plan: blank(details.plan) }
      : null,
  };
}

export function DetailsFields({ value, onChange }: { value: Details; onChange: (value: Details) => void }) {
  return (
    <Stack>
      <TextInput label="Fecha de nacimiento" type="date" w={200} value={value.birthDate}
        onChange={(event) => onChange({ ...value, birthDate: event.currentTarget.value })} />
      <ContactFields label="Teléfono" type="tel" value={value.phone} onChange={(phone) => onChange({ ...value, phone })} />
      <ContactFields label="Email" type="email" value={value.email} onChange={(email) => onChange({ ...value, email })} />
      {/* Left empty, the patient has no insurance on the books; whether they pay privately is not said (D-74). */}
      <Group align="flex-end" wrap="wrap">
        <TextInput label="Obra social o prepaga" maxLength={100} value={value.insurer}
          onChange={(event) => onChange({ ...value, insurer: event.currentTarget.value })} />
        <TextInput label="N° de afiliado" maxLength={30} value={value.memberNumber}
          onChange={(event) => onChange({ ...value, memberNumber: event.currentTarget.value })} />
        <TextInput label="Plan" maxLength={50} value={value.plan}
          onChange={(event) => onChange({ ...value, plan: event.currentTarget.value })} />
      </Group>
    </Stack>
  );
}
