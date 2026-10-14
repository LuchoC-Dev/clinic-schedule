import { shortDay } from '../../shared/dates';
import type { Owner, Patient } from '../types';
import { CONSENT, DOCUMENT_TYPE_NAMES, IDENTITY, RELATIONS } from '../words';

/**
 * What a patient's screen says of them and their history, in the words of the Thymeleaf screen (D-60, D-73, D-77).
 */

export interface IdentityChange {
  changedAt: string;
  changedBy: string;
  before: Identity;
  after: Identity;
}

interface Identity {
  fullName: string;
  document: { type: string; number: string } | null;
  identity: string;
}

export interface DetailsChange {
  changedAt: string;
  changedBy: string;
  field: string;
  before: DetailValue | null;
  after: DetailValue | null;
}

interface DetailValue {
  value: string | null;
  owner: Owner | null;
  memberNumber: string | null;
  plan: string | null;
}

/** `Ana Pérez, DNI 30111222 — Verificada`, or `NN-4821, sin documento — Sin datos`. */
export function identityText(identity: Identity): string {
  const document = identity.document
    ? `${DOCUMENT_TYPE_NAMES[identity.document.type] ?? identity.document.type} ${identity.document.number}`
    : null;
  return `${identity.fullName}${document ? `, ${document}` : ', sin documento'} — ${IDENTITY[identity.identity] ?? identity.identity}`;
}

/** A phone or an email with whose it is: `1145556666 (Madre: Ana Pérez)`, or just the value. */
export function withOwner(value: string, owner: Owner | null): string {
  return owner ? `${value} (${RELATIONS[owner.relation] ?? owner.relation}: ${owner.name})` : value;
}

/** `OSDE · plan 310 · afiliado 61234567801`. */
export function insuranceText(insurance: { insurer: string; memberNumber: string | null; plan: string | null }): string {
  return insurance.insurer + (insurance.plan ? ` · plan ${insurance.plan}` : '')
    + (insurance.memberNumber ? ` · afiliado ${insurance.memberNumber}` : '');
}

/** One side of a change of the details, as its history lists it. */
export function detailText(field: string, value: DetailValue | null): string {
  if (!value || value.value === null) {
    return '—';
  }
  switch (field) {
    case 'BIRTH_DATE':
      return shortDay(value.value);
    case 'INSURANCE':
      return insuranceText({ insurer: value.value, memberNumber: value.memberNumber, plan: value.plan });
    case 'PHONE_CONSENT':
    case 'EMAIL_CONSENT':
      return CONSENT[value.value] ?? value.value;
    default:
      return withOwner(value.value, value.owner);
  }
}

/**
 * Who may agree to being reminded on a channel (D-77): nobody, the patient, or — when the phone or the email is
 * someone else's — that person, named. None without the channel.
 */
export function consentChoices(channel: Patient['phone'] | Patient['email']): { value: string; label: string }[] {
  if (!channel) {
    return [];
  }
  const choices = [{ value: '', label: 'Sin consentimiento' }, { value: 'PATIENT', label: 'Lo dio el paciente' }];
  if (channel.owner) {
    choices.push({ value: 'OWNER', label: `Lo dio ${RELATIONS[channel.owner.relation] ?? channel.owner.relation}: ${channel.owner.name}` });
  }
  return choices;
}
