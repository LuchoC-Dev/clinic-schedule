import type { AgeBand } from '../family/family';

/**
 * What *Mis datos* decides (D-81): the contact and insurance as they are typed and sent, and the consent each channel
 * offers, as the Thymeleaf screen did.
 */

export type Consent = 'PATIENT' | 'OWNER';

export interface Channel {
  value: string;
  owner: { name: string; relation: string } | null;
  consent: Consent | null;
}

export interface Profile {
  fullName: string;
  document: string;
  birthDate: string | null;
  ageBand: AgeBand | null;
  phone: Channel | null;
  email: Channel | null;
  insurance: { insurer: string; memberNumber: string | null; plan: string | null } | null;
}

/** The relations an owner may have to the patient, in Spanish. */
export const RELATIONS = [
  { value: 'MOTHER', label: 'Madre' }, { value: 'FATHER', label: 'Padre' },
  { value: 'GUARDIAN', label: 'Tutor o tutora' }, { value: 'PARTNER', label: 'Pareja' },
  { value: 'CHILD', label: 'Hijo o hija' }, { value: 'OTHER', label: 'Otra persona' },
];

export interface DetailsForm {
  phoneNumber: string;
  phoneOwnerRelation: string;
  phoneOwnerName: string;
  emailAddress: string;
  emailOwnerRelation: string;
  emailOwnerName: string;
  insurer: string;
  memberNumber: string;
  plan: string;
}

export function detailsFormOf(profile: Profile): DetailsForm {
  return {
    phoneNumber: profile.phone?.value ?? '', phoneOwnerRelation: profile.phone?.owner?.relation ?? '',
    phoneOwnerName: profile.phone?.owner?.name ?? '', emailAddress: profile.email?.value ?? '',
    emailOwnerRelation: profile.email?.owner?.relation ?? '', emailOwnerName: profile.email?.owner?.name ?? '',
    insurer: profile.insurance?.insurer ?? '', memberNumber: profile.insurance?.memberNumber ?? '',
    plan: profile.insurance?.plan ?? '',
  };
}

/**
 * What `PUT /api/portal/profile/details` is sent: the form as typed, as the Thymeleaf form sent it — an owner only
 * when one is chosen or named, the rules of the patient module saying what is missing (D-81).
 */
export function detailsBody(form: DetailsForm) {
  const owner = (relation: string, name: string) => (relation === '' && name.trim() === '' ? null : { name, relation });
  return {
    phone: { number: form.phoneNumber, owner: owner(form.phoneOwnerRelation, form.phoneOwnerName) },
    email: { address: form.emailAddress, owner: owner(form.emailOwnerRelation, form.emailOwnerName) },
    insurance: { insurer: form.insurer, memberNumber: form.memberNumber, plan: form.plan },
  };
}

/**
 * The choices for a channel's reminders, the one that stands first (D-81): "" keeps the consent its owner gave,
 * `true` is the patient's own, `false` is none — so saving the form untouched never agrees. None for a channel that
 * is not there.
 */
export function consentChoices(channel: Channel | null): { value: '' | 'true' | 'false'; label: string }[] {
  if (!channel) {
    return [];
  }
  const own = { value: 'true' as const, label: 'Sí, lo autorizo yo' };
  const none = { value: 'false' as const, label: 'No' };
  if (channel.consent === 'OWNER') {
    return [{ value: '', label: `Sí: lo autorizó ${channel.owner?.name ?? ''}` }, own, none];
  }
  return channel.consent === null ? [none, own] : [own, none];
}

/** A choice as `PUT /api/portal/profile/contact-consent` takes it: "" and a channel not offered left as they are. */
export function consentValue(choice: string | undefined): boolean | null {
  return choice === 'true' ? true : choice === 'false' ? false : null;
}

/** `14/05/1990`, as the Thymeleaf screen said it. */
export function birthDateSaid(day: string): string {
  const [year, month, date] = day.split('-');
  return `${Number(date)}/${month}/${year}`;
}

export const DETAILS_REFUSED = 'Revisá tus datos: el teléfono lleva de 8 a 15 dígitos; el email, la forma '
  + 'nombre@dominio.com; si el teléfono o el email son de otra persona, van con su nombre y su vínculo; y el número de '
  + 'afiliado y el plan van con el nombre de la obra social.';

export const CONSENT_REFUSED = 'Para recibir recordatorios en un teléfono o un email, primero cargalo en tus datos.';
