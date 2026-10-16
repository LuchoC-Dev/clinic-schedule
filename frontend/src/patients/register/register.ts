import type { ApiError } from '../../shared/api';
import { byCode, combine } from '../../shared/refusals';
import { lengthWrong, PASSWORD_LENGTH } from '../account/account';

/**
 * What registering on the web decides (D-91, D-92): the form of an adult and up to three children under 13, what is
 * sent of it, and each refusal in the Thymeleaf screen's words.
 */

export type Relation = 'MOTHER' | 'FATHER' | 'GUARDIAN';

export const RELATIONS: { value: Relation; label: string }[] = [
  { value: 'MOTHER', label: 'madre' }, { value: 'FATHER', label: 'padre' },
  { value: 'GUARDIAN', label: 'tutor o tutora' },
];

/** How many children the form offers to register with the adult; the API takes up to five. */
export const CHILD_SLOTS = 3;

export interface ChildForm {
  fullName: string;
  documentType: string;
  documentNumber: string;
  birthDate: string;
  relation: Relation;
}

export interface RegistrationForm {
  fullName: string;
  documentType: string;
  documentNumber: string;
  birthDate: string;
  phone: string;
  email: string;
  password: string;
  repeated: string;
  children: ChildForm[];
}

export function emptyChild(documentType: string): ChildForm {
  return { fullName: '', documentType, documentNumber: '', birthDate: '', relation: 'MOTHER' };
}

export function emptyRegistration(documentType: string): RegistrationForm {
  return {
    fullName: '', documentType, documentNumber: '', birthDate: '', phone: '', email: '', password: '', repeated: '',
    children: Array.from({ length: CHILD_SLOTS }, () => emptyChild(documentType)),
  };
}

/** A child's block left empty is no child. */
export function typed(child: ChildForm): boolean {
  return [child.fullName, child.documentNumber, child.birthDate].some((value) => value.trim() !== '');
}

const orNull = (value: string) => (value.trim() === '' ? null : value);

/** What `POST /api/portal/registrations` is sent: what was typed, an empty value as none. */
export function registrationBody(form: RegistrationForm) {
  return {
    fullName: form.fullName, documentType: form.documentType, documentNumber: form.documentNumber,
    birthDate: orNull(form.birthDate), phone: orNull(form.phone), email: form.email, password: form.password,
    children: form.children.filter(typed).map((child) => ({
      fullName: child.fullName, documentType: child.documentType, documentNumber: child.documentNumber,
      birthDate: orNull(child.birthDate), relation: child.relation,
    })),
  };
}

/** What the form missed, among what the rules refuse as malformed: the API says only that it is. */
export function malformed(form: RegistrationForm): string {
  if (form.birthDate.trim() === '') {
    return 'Falta tu fecha de nacimiento.';
  }
  if (form.children.filter(typed).some((child) => child.birthDate.trim() === '')) {
    return 'Falta la fecha de nacimiento de uno de tus hijos.';
  }
  if (lengthWrong(form.password)) {
    return PASSWORD_LENGTH;
  }
  return 'Pusiste el mismo documento dos veces.';
}

export function registrationRefusals(form: RegistrationForm) {
  return combine(byCode({
    INVALID_REGISTRATION: 'Revisá los datos: los nombres, los documentos, las fechas de nacimiento y el teléfono, si '
      + 'lo pusiste.',
    UNDER_EIGHTEEN: 'Desde la web se registran mayores de 18 años. Si sos menor, tu mamá, tu papá o quien te cuide '
      + 'puede registrarte en recepción.',
    NOT_A_CHILD: 'Desde la web se registran hijos menores de 13. Desde los 13, se registran en recepción.',
    INVALID_ACCOUNT_EMAIL: 'El email tiene la forma nombre@dominio.com y hasta 254 caracteres.',
  }), (error: ApiError) => {
    if (error.body?.code === 'ILLEGAL_ARGUMENT') {
      return malformed(form);
    }
    return error.status === 503 ? 'No pudimos mandarte el email. Probá de nuevo en un rato.' : undefined;
  });
}

export const UNUSABLE_CONFIRMATION = 'Este link ya no sirve: venció o ya se usó. Si todavía no te registraste, '
  + 'registrate de nuevo.';

export const DOCUMENT_KNOWN = 'No pudimos completar tu registro con ese documento. Acercate a recepción con tu '
  + 'documento y lo resolvemos.';

/** What the login says of the children a confirmation could not register (D-92). */
export function notRegisteredSaid(names: string[]): string | null {
  return names.length === 0 ? null
    : `No pudimos registrar a ${names.join(', ')} con ese documento: acercate a recepción con su documento.`;
}
