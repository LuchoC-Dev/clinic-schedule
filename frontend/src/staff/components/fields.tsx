import { Checkbox, Group, NativeSelect, TextInput } from '@mantine/core';

import { DOCUMENT_TYPE_NAMES, DOCUMENT_TYPES, MEANS, RELATIONS } from '../words';

/** How a charge is paid (D-89): cash unless reception says otherwise. */
export function MeansSelect({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <NativeSelect label="Medio de pago" value={value} onChange={(event) => onChange(event.currentTarget.value)}
      data={Object.entries(MEANS).map(([means, name]) => ({ value: means, label: name }))} />
  );
}

export interface Coverage {
  covered: boolean;
  token: string;
}

/**
 * Whether the patient's insurance covers the visit (D-90): the insurer was asked outside the application and said
 * yes. The token, when its check asks for one. The amount beside it is then the copay, or nothing.
 */
export function CoverageFields({ value, onChange }: { value: Coverage; onChange: (value: Coverage) => void }) {
  return (
    <Group align="flex-end">
      <Checkbox label="Con cobertura" checked={value.covered}
        onChange={(event) => onChange({ ...value, covered: event.currentTarget.checked })} />
      {value.covered && (
        <TextInput label="Token (si lo pide)" maxLength={50} value={value.token}
          onChange={(event) => onChange({ ...value, token: event.currentTarget.value })} />
      )}
    </Group>
  );
}

/** The coverage as the API takes it, or none. */
export function coverageOf(value: Coverage): { token: string | null } | undefined {
  return value.covered ? { token: value.token.trim() || null } : undefined;
}

export interface Contact {
  value: string;
  /** Empty when it is the patient's own. */
  relation: string;
  ownerName: string;
}

export const NO_CONTACT: Contact = { value: '', relation: '', ownerName: '' };

/** A phone or an email, saying whose it is (D-73): the patient's own, or someone's with their name. */
export function ContactFields({ label, type, value, onChange }: {
  label: string;
  type: 'tel' | 'email';
  value: Contact;
  onChange: (value: Contact) => void;
}) {
  return (
    <Group align="flex-end" wrap="wrap">
      <TextInput label={label} type={type} maxLength={type === 'tel' ? 30 : 254} value={value.value}
        onChange={(event) => onChange({ ...value, value: event.currentTarget.value })} />
      <NativeSelect label="De quién es" value={value.relation}
        onChange={(event) => onChange({ ...value, relation: event.currentTarget.value })}
        data={[{ value: '', label: 'Del paciente' },
          ...Object.entries(RELATIONS).map(([relation, name]) => ({ value: relation, label: name }))]} />
      {value.relation && (
        <TextInput label="Nombre de esa persona" maxLength={150} value={value.ownerName}
          onChange={(event) => onChange({ ...value, ownerName: event.currentTarget.value })} />
      )}
    </Group>
  );
}

/** A contact as the API takes it, or none when nothing was typed. */
export function contactOf(value: Contact, key: 'number' | 'address' | 'value') {
  if (!value.value.trim()) {
    return null;
  }
  return {
    [key]: value.value.trim(),
    owner: value.relation ? { name: value.ownerName.trim(), relation: value.relation } : null,
  };
}

export interface DocumentInput {
  type: string;
  number: string;
}

/** An identity document, or none (D-26, D-60). */
export function DocumentFields({ value, onChange }: { value: DocumentInput; onChange: (value: DocumentInput) => void }) {
  return (
    <Group align="flex-end">
      <NativeSelect label="Documento" value={value.type}
        onChange={(event) => onChange({ ...value, type: event.currentTarget.value })}
        data={[{ value: '', label: '— sin documento —' },
          ...DOCUMENT_TYPES.map((type) => ({ value: type, label: DOCUMENT_TYPE_NAMES[type] }))]} />
      <TextInput label="Número" maxLength={20} value={value.number}
        onChange={(event) => onChange({ ...value, number: event.currentTarget.value })} />
    </Group>
  );
}
