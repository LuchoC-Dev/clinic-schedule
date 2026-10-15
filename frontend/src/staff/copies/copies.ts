import { byCode } from '../../shared/refusals';

/**
 * What the copies of a record decide on the practitioners' screens (D-95, D-106): who asked, how it is delivered, how
 * long is left of the 48 hours. Nothing of the record itself: that is the copy's PDF, a reading of its own.
 */

export interface Person {
  id: string;
  fullName: string | null;
  document: string | null;
}

/** A copy as `/api/record-copies` answers it. */
export interface RecordCopy {
  id: string;
  patient: Person;
  requestedBy: Person;
  requestedAt: string;
  delivery: 'PDF' | 'PAPER';
  code: string;
  assembledAt: string;
  assembly: number;
  deadline: string;
  /** Negative once late. */
  minutesLeft: number;
  late: boolean;
  authentication: { practitionerId: string; practitionerName: string | null; username: string; at: string } | null;
  handedOverAt: string | null;
  mayRead: boolean;
}

/** `Ana Pérez — DNI 30111222`. */
export function personOf(person: Person, unknown = '(paciente desconocido)'): string {
  if (!person.fullName) {
    return unknown;
  }
  return person.document ? `${person.fullName} — ${person.document}` : person.fullName;
}

/** Who asked: the patient themselves, or their parent (D-95). */
export function askedBy(copy: RecordCopy): string {
  return copy.requestedBy.id === copy.patient.id ? 'El paciente' : (copy.requestedBy.fullName ?? '(desconocido)');
}

export function deliveryOf(copy: RecordCopy): string {
  return copy.delivery === 'PDF' ? 'PDF en su cuenta' : 'Papel, en recepción';
}

/** "Quedan 31 h", "Quedan 40 min", "Vencida hace 3 h": how long is left of the 48 hours, or how late it is. */
export function timeLeft(minutesLeft: number): string {
  const amount = Math.abs(minutesLeft);
  const hours = Math.floor(amount / 60);
  const said = hours >= 1 ? `${hours} h` : `${Math.max(1, amount)} min`;
  return minutesLeft < 0 ? `Vencida hace ${said}` : `Quedan ${said}`;
}

/** What the practitioner is told once they authenticated it: what to do with the paper, or that the patient has it. */
export function authenticatedNotice(copy: RecordCopy): string {
  return copy.delivery === 'PAPER'
    ? 'Copia autenticada. Imprimila, firmala, sellala y dejala en recepción en un sobre cerrado.'
    : 'Copia autenticada. El paciente ya puede descargarla desde su cuenta.';
}

/** Checking a copy by its code. */
export const CODE_REFUSALS = byCode({ RECORD_COPY_NOT_FOUND: 'No hay ninguna copia con ese código.' });

/** Putting it together again (D-95). */
export const REASSEMBLY_REFUSALS = byCode({
  ALREADY_AUTHENTICATED: 'La copia ya está autenticada: no se arma de nuevo.',
  RECORD_COPY_CHANGED: 'La copia ya está autenticada: no se arma de nuevo.',
  NO_ACCESS: 'No podés leer esta historia clínica.',
});

/** Authenticating it (D-95). */
export const AUTHENTICATION_REFUSALS = byCode({
  NOT_REVIEWED: 'Marcá que revisaste la copia antes de autenticarla.',
  REASSEMBLED_SINCE: 'La copia se armó de nuevo después de que la abriste: revisala otra vez.',
  ALREADY_AUTHENTICATED: 'La copia ya está autenticada.',
  RECORD_COPY_CHANGED: 'La copia ya está autenticada.',
  NO_ACCESS: 'No podés leer esta historia clínica.',
});

/** Asking for access to authenticate it (D-95). */
export const COPY_ACCESS_REFUSALS = byCode({
  REASON_REQUIRED: 'Escribí el motivo del pedido de acceso.',
  ALREADY_AUTHENTICATED: 'La copia ya está autenticada: no hace falta acceso para eso.',
  ILLEGAL_ARGUMENT: 'El motivo tiene hasta 500 caracteres.',
});
