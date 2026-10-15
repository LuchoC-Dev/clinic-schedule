import { byCode } from '../../shared/refusals';
import type { Total } from '../cash/cash';
import type { Role } from '../navigation';

/** A user of the practice as the API answers it (D-68): never their password. */
export interface User {
  id: string;
  username: string;
  role: Role;
  practitionerId: string | null;
  active: boolean;
}

export interface UserChange {
  action: 'CREATED' | 'DEACTIVATED' | 'REACTIVATED' | 'PASSWORD_RESET' | 'PASSWORD_CHANGED';
  changedBy: string;
  changedAt: string;
}

/** Whose record, as the logs name a patient; `document` is null when they gave none. */
export interface LoggedPatient {
  id: string;
  fullName: string | null;
  document: string | null;
}

export interface LoggedPractitioner {
  id: string;
  fullName: string | null;
}

/** The readings of clinical records (D-71): who, whose, when, by which access — never what was read. */
export interface Readings {
  from: string;
  to: string;
  limitReached: boolean;
  readings: {
    readAt: string;
    /** Null for a copy handed to whoever asked for it (D-96). */
    practitioner: LoggedPractitioner | null;
    /** Null for a reading recorded before users were. */
    readBy: string | null;
    patient: LoggedPatient;
    access: 'VISIT_TODAY' | 'ACCESS_GRANT' | 'SEEN_WITHIN_PERIOD' | 'COPY_REQUEST';
    attachment: boolean;
    copy: boolean;
  }[];
}

/** The refused attempts to reach clinical content (D-72). */
export interface Refusals {
  from: string;
  to: string;
  limitReached: boolean;
  refusals: {
    refusedAt: string;
    refusedBy: string;
    practitioner: LoggedPractitioner | null;
    patient: LoggedPatient | null;
    attempt: 'RECORD' | 'ATTACHMENT' | 'ROUTE';
    route: string | null;
  }[];
}

/** The cancellations as the admins read them (D-76): never the patient nor reception's note. */
export interface CancellationLog {
  from: string;
  to: string;
  limitReached: boolean;
  cancellations: {
    cancelledAt: string | null;
    /** Null when the patient cancelled, on the web or from an email's link (D-83). */
    cancelledBy: string | null;
    origin: string;
    requestedBy: 'PATIENT' | 'PRACTICE' | null;
    practitioner: LoggedPractitioner;
    startsAt: string;
  }[];
}

/** A day of the cash desk as the admins read it (D-87): never the patients nor a refund's reason. */
export interface CashLog {
  date: string;
  movements: { kind: 'CHARGE' | 'REFUND'; amount: number; means: string; recordedBy: string; recordedAt: string }[];
  total: Total;
  byRecorder: (Total & { recordedBy: string })[];
}

/** The shortest a password may be (D-69); the server checks it, and its ceiling, anyway. */
export const PASSWORD_MIN_LENGTH = 10;

/** The roles in the order the admin chooses them. */
export const ROLES: Role[] = ['RECEPTION', 'PRACTITIONER', 'ADMIN', 'WAITING_ROOM'];

export const USER_CHANGES: Record<UserChange['action'], string> = {
  CREATED: 'Creó el usuario',
  DEACTIVATED: 'Lo desactivó',
  REACTIVATED: 'Lo reactivó',
  PASSWORD_RESET: 'Blanqueó la contraseña',
  PASSWORD_CHANGED: 'Cambió su contraseña',
};

export const USER_REFUSALS = byCode({
  USERNAME_TAKEN: 'Ese nombre de usuario ya se usó. Un nombre de usuario no se repite, aunque el anterior esté desactivado.',
  PRACTITIONER_TAKEN: 'Ese profesional ya tiene un usuario activo.',
  UNKNOWN_PRACTITIONER: 'Ese profesional no existe.',
  SELF_DEACTIVATION: 'No podés desactivar tu propio usuario.',
  USER_STATUS: 'El usuario ya estaba así.',
  USER_NOT_FOUND: 'Ese usuario no existe.',
  ILLEGAL_ARGUMENT: `Revisá los datos: el usuario lleva de 3 a 50 minúsculas, números, puntos, guiones o guiones bajos; el `
    + `rol es obligatorio, y el de un profesional pide elegir el profesional; la contraseña, de ${PASSWORD_MIN_LENGTH} a `
    + '72 caracteres.',
});

/** Two days the wrong way round, the only refusal of the logs. */
export const LOG_REFUSALS = byCode({ ILLEGAL_ARGUMENT: 'El primer día es posterior al último.' });

/** A new user as it is sent: the practitioner only for a practitioner's user, as the server asks. */
export function newUser(form: { username: string; role: Role | ''; practitionerId: string; password: string }) {
  return {
    username: form.username.trim(),
    role: form.role || null,
    practitionerId: form.role === 'PRACTITIONER' && form.practitionerId ? form.practitionerId : null,
    password: form.password,
  };
}

/** `Activo` or `Desactivado`. */
export function userState(user: Pick<User, 'active'>): string {
  return user.active ? 'Activo' : 'Desactivado';
}

/** `Ana Pérez — DNI 30111222`, as the logs name a patient. */
export function loggedPatient(patient: LoggedPatient | null): string {
  if (!patient) return '—';
  if (!patient.fullName) return '(paciente desconocido)';
  return patient.document ? `${patient.fullName} — ${patient.document}` : patient.fullName;
}

export function loggedPractitioner(practitioner: LoggedPractitioner | null): string {
  if (!practitioner) return '—';
  return practitioner.fullName ?? '(profesional desconocido)';
}

export const READ_ACCESS: Record<Readings['readings'][number]['access'], string> = {
  VISIT_TODAY: 'Lo atiende hoy',
  ACCESS_GRANT: 'Acceso pedido',
  SEEN_WITHIN_PERIOD: 'Lo atendió dentro del período',
  COPY_REQUEST: 'Copia pedida por el paciente',
};

/** What was read: a file, a copy of the record, or the record — never which file (D-71). */
export function readingWhat(reading: Pick<Readings['readings'][number], 'attachment' | 'copy'>): string {
  if (reading.attachment) return 'Archivo adjunto';
  return reading.copy ? 'Copia de la historia' : 'Historia';
}

/** What was tried: a record, a file, or a clinical route with its method and path. */
export function refusalWhat(refusal: Pick<Refusals['refusals'][number], 'attempt' | 'route'>): string {
  switch (refusal.attempt) {
    case 'RECORD':
      return 'Historia';
    case 'ATTACHMENT':
      return 'Archivo adjunto';
    default:
      return `Ruta ${refusal.route ?? ''}`.trim();
  }
}

/** Who cancelled, as the admins read it: the patient on the web, never their account (D-83). */
export function cancelledByForAdmins(entry: Pick<CancellationLog['cancellations'][number], 'origin' | 'cancelledBy'>): string {
  if (entry.origin === 'ONLINE' || entry.origin === 'EMAIL_LINK') return 'paciente (web)';
  return entry.cancelledBy ?? '—';
}
